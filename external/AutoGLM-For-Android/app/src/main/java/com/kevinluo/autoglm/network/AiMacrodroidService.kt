package com.kevinluo.autoglm.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kevinluo.autoglm.ComponentManager
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.network.model.EventReq
import com.kevinluo.autoglm.network.model.TaskDto
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.task.TaskEventType
import com.kevinluo.autoglm.task.TaskExecutionManager
import com.kevinluo.autoglm.network.model.DeviceRegisterReq
import com.kevinluo.autoglm.network.model.HeartbeatReq
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import kotlin.math.min

private data class RemoteTaskRuntime(
    val taskId: String,
    val description: String,
    val retryMax: Int,
    val retryBackoffMs: Long,
    val stepTimeoutMs: Long,
    val deadlineAtMs: Long?,
    var attempt: Int = 1,
    var attemptStartedAt: Long = System.currentTimeMillis(),
    var currentStepNo: Int? = null,
    var stepStartedAt: Long? = null,
    var latestThinking: String? = null,
    var latestScreenshot: String? = null,
    var lastUploadedScreenshotDigest: String? = null,
    var guardCancelled: Boolean = false,
    var guardCancelReason: String? = null,
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Background service for device registration and heartbeat.
 */
class AiMacrodroidService : Service() {

    companion object {
        private const val TAG = "AiMacrodroidService"
        private const val NOTIFICATION_ID = 2001
        private const val HEARTBEAT_INTERVAL_MS = 3_000L
        private const val POLL_TASK_INTERVAL_MS = 5_000L
        private const val EVENT_RETRY_BASE_BACKOFF_MS = 1_000L
        private const val EVENT_RETRY_LOOP_INTERVAL_MS = 2_000L
        private const val EVENT_RETRY_MAX = 5
        private const val LOCAL_EVENT_QUEUE_MAX_SIZE = 1_000
        private const val STEP_DEFAULT_TIMEOUT_MS = 5_000L
        private const val DEADLINE_CHECK_INTERVAL_MS = 1_000L

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _currentRemoteTask = MutableStateFlow<String?>(null)
        val currentRemoteTask: StateFlow<String?> = _currentRemoteTask.asStateFlow()
        private val _pendingRemoteTasks = MutableStateFlow<List<TaskDto>>(emptyList())
        val pendingRemoteTasks: StateFlow<List<TaskDto>> = _pendingRemoteTasks.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AiMacrodroidService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AiMacrodroidService::class.java))
            _connectionState.value = ConnectionState.DISCONNECTED
            _currentRemoteTask.value = null
            _pendingRemoteTasks.value = emptyList()
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeLock = Any()
    private lateinit var eventRetryStore: EventRetryStore
    private val pendingEvents = mutableListOf<QueuedEvent>()
    private val taskRuntimeMap = mutableMapOf<String, RemoteTaskRuntime>()
    private var heartbeatJob: Job? = null
    private var pollJob: Job? = null
    private var observeJob: Job? = null
    private var retryJob: Job? = null
    private var guardJob: Job? = null
    private var pollPausedByTask = false

    override fun onCreate() {
        super.onCreate()
        eventRetryStore = EventRetryStore(this)
        pendingEvents.addAll(eventRetryStore.load())
        Logger.i(TAG, "AiMacrodroidService created")
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "AiMacrodroidService started")
        if (heartbeatJob?.isActive != true) {
            heartbeatJob = serviceScope.launch {
                try {
                    if (registerDeviceIfNeeded()) {
                        startHeartbeatLoop()
                    } else {
                        Logger.e(TAG, "Device registration failed, cannot start heartbeat")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error in AiMacrodroidService loop", e)
                }
            }
        }
        if (pollJob?.isActive != true) {
            pollJob = serviceScope.launch {
                try {
                    startTaskPollLoop()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error in AiMacrodroidService poll task loop", e)
                }
            }
        }
        if (observeJob?.isActive != true) {
            observeJob = serviceScope.launch {
                try {
                    observeTaskEvents()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error observing task events", e)
                }
            }
        }
        if (retryJob?.isActive != true) {
            retryJob = serviceScope.launch {
                try {
                    startEventRetryLoop()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error in event retry loop", e)
                }
            }
        }
        if (guardJob?.isActive != true) {
            guardJob = serviceScope.launch {
                try {
                    startRuntimeGuardLoop()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error in runtime guard loop", e)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "AiMacrodroidService destroyed")
        synchronized(runtimeLock) {
            pollPausedByTask = false
        }
        _pendingRemoteTasks.value = emptyList()
        serviceScope.cancel()
    }

    private suspend fun registerDeviceIfNeeded(): Boolean {
        val tokenManager = TokenManager.getInstance(this)
        if (!tokenManager.getToken().isNullOrBlank()) {
            Logger.d(TAG, "Device already registered, token exists")
            return true
        }

        val componentManager = ComponentManager.getInstance(this)
        val deviceManager = componentManager.deviceManager
        val settingsManager = componentManager.settingsManager

        if (settingsManager.getAiMacrodroidBaseUrl().isBlank()) {
            Logger.w(TAG, "Base URL not configured, skipping registration")
            return false
        }

        try {
            val req = DeviceRegisterReq(
                deviceId = deviceManager.deviceId,
                brand = deviceManager.brand,
                model = deviceManager.model,
                androidVersion = deviceManager.androidVersion,
                resolution = deviceManager.getResolution(),
                shizukuAvailable = deviceManager.isShizukuAvailable(),
                overlayGranted = deviceManager.isOverlayGranted(),
                keyboardEnabled = deviceManager.isKeyboardEnabled(),
                capabilities = deviceManager.getCapabilities()
            )

            Logger.d(TAG, "Registering device: $req")
            _connectionState.value = ConnectionState.CONNECTING
            val resp = AiMacrodroidApiClient.getInstance(this).api.registerDevice(req)
            val registerData = resp.data
            if (resp.isSuccess() && registerData?.registered == true) {
                tokenManager.saveToken(registerData.token)
                _connectionState.value = ConnectionState.CONNECTED
                Logger.i(TAG, "Device registered successfully")
                return true
            } else {
                _connectionState.value = ConnectionState.ERROR
                Logger.e(TAG, "Device registration returned false")
                return false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Device registration failed", e)
            _connectionState.value = ConnectionState.ERROR
            return false
        }
    }

    private suspend fun startHeartbeatLoop() {
        val componentManager = ComponentManager.getInstance(this)
        val deviceManager = componentManager.deviceManager
        val settingsManager = componentManager.settingsManager

        while (serviceScope.isActive) {
            if (settingsManager.getAiMacrodroidBaseUrl().isBlank()) {
                delay(HEARTBEAT_INTERVAL_MS)
                continue
            }

            try {
                val req = HeartbeatReq(
                    deviceId = deviceManager.deviceId,
                    foregroundPkg = deviceManager.getForegroundPkg(),
                    batteryPct = deviceManager.getBatteryPct(),
                    networkType = deviceManager.getNetworkType(),
                    charging = deviceManager.isCharging(),
                    shizukuAvailable = deviceManager.isShizukuAvailable(),
                    overlayGranted = deviceManager.isOverlayGranted(),
                    keyboardEnabled = deviceManager.isKeyboardEnabled(),
                    capabilities = deviceManager.getCapabilities(),
                    sseSupported = true
                )

                Logger.d(TAG, "Sending heartbeat...")
                val resp = AiMacrodroidApiClient.getInstance(this).api.heartbeat(req)
                if (resp.isSuccess()) {
                    Logger.d(TAG, "Heartbeat ok")
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    Logger.w(TAG, "Heartbeat returned not ok, code=${resp.codeText()}, message=${resp.message}")
                    if (resp.needReRegister()) {
                        TokenManager.getInstance(this).clearToken()
                        val registered = registerDeviceIfNeeded()
                        _connectionState.value = if (registered) ConnectionState.CONNECTED else ConnectionState.ERROR
                    } else {
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Heartbeat failed", e)
                _connectionState.value = ConnectionState.ERROR
            }

            delay(HEARTBEAT_INTERVAL_MS)
        }
    }

    private suspend fun startTaskPollLoop() {
        val componentManager = ComponentManager.getInstance(this)
        val deviceManager = componentManager.deviceManager
        val settingsManager = componentManager.settingsManager
        val taskExecutionManager = TaskExecutionManager

        while (serviceScope.isActive) {
            if (settingsManager.getAiMacrodroidBaseUrl().isBlank()) {
                delay(POLL_TASK_INTERVAL_MS)
                continue
            }

            try {
                val hasInFlightTask = synchronized(runtimeLock) {
                    _currentRemoteTask.value != null || taskRuntimeMap.isNotEmpty()
                }
                val pausedByTask = synchronized(runtimeLock) { pollPausedByTask }
                if (hasInFlightTask || pausedByTask) {
                    Logger.d(TAG, "Remote task in-flight, skipping poll")
                } else if (taskExecutionManager.canStartTask()) {
                    Logger.d(TAG, "Polling for tasks...")
                    val tasks = AiMacrodroidApiClient.getInstance(this).api.pollTasks(deviceManager.deviceId).data.orEmpty()
                    _pendingRemoteTasks.value = tasks
                    if (tasks.isNotEmpty()) {
                        Logger.i(TAG, "Received ${tasks.size} tasks")
                        val taskToExecute = tasks.firstOrNull()
                        if (taskToExecute != null) {
                            synchronized(runtimeLock) {
                                pollPausedByTask = true
                            }
                            _pendingRemoteTasks.value = tasks.filterNot { it.id == taskToExecute.id }
                            executeRemoteTask(taskToExecute, taskExecutionManager)
                        }
                    }
                } else {
                    Logger.d(TAG, "Cannot start task currently, skipping poll")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Task polling failed", e)
                _pendingRemoteTasks.value = emptyList()
            }

            delay(POLL_TASK_INTERVAL_MS)
        }
    }

    private fun executeRemoteTask(taskDto: TaskDto, taskExecutionManager: TaskExecutionManager) {
        Logger.i(TAG, "Executing remote task: ${taskDto.id} (track: ${taskDto.track})")
        val description = buildTaskDescription(taskDto)
        if (description.isNullOrBlank()) {
            synchronized(runtimeLock) {
                pollPausedByTask = false
            }
            serviceScope.launch {
                reportSyntheticFail(taskDto.id, "STEP_PARAM_INVALID", "Task description is empty")
            }
            return
        }
        val retryMax = readInt(taskDto.constraints, "maxRetries")
            ?: readInt(taskDto.retryPolicy, "maxRetries")
            ?: 0
        val retryBackoffMs = readLong(taskDto.retryPolicy, "retryBackoffMs")
            ?: readLong(taskDto.retryPolicy, "backoffMs")
            ?: EVENT_RETRY_BASE_BACKOFF_MS
        val stepTimeoutMs = readLong(taskDto.constraints, "stepTimeoutMs")
            ?: readLong(taskDto.loop, "stepTimeoutMs")
            ?: resolveStepTimeoutFromCommands(taskDto)
            ?: STEP_DEFAULT_TIMEOUT_MS
        val deadlineAtMs = resolveDeadlineAt(taskDto)
        val runtime = RemoteTaskRuntime(
            taskId = taskDto.id,
            description = description,
            retryMax = retryMax,
            retryBackoffMs = retryBackoffMs,
            stepTimeoutMs = stepTimeoutMs,
            deadlineAtMs = deadlineAtMs,
        )
        synchronized(runtimeLock) {
            taskRuntimeMap[taskDto.id] = runtime
        }
        _currentRemoteTask.value = taskDto.id
        startTaskWithRuntime(runtime, taskExecutionManager)
    }

    private fun buildTaskDescription(taskDto: TaskDto): String? {
        val normalizedTrack = taskDto.track.lowercase()
        return if (normalizedTrack == "intent" || normalizedTrack == "intent-based") {
            val intentStr = taskDto.intent
            if (!intentStr.isNullOrBlank()) {
                intentStr
            } else {
                Logger.e(TAG, "Task ${taskDto.id} has intent track but empty intent string")
                null
            }
        } else if (normalizedTrack == "atomic") {
            val commands = taskDto.commands
            if (!commands.isNullOrEmpty()) {
                val promptBuilder = StringBuilder("请按顺序执行以下操作：\n")
                commands.forEachIndexed { index, cmd ->
                    val action = (cmd["action"] ?: cmd["actionCode"])?.let { if (it is JsonPrimitive) it.content else it.toString() } ?: "unknown"
                    val stepName = cmd["stepName"]?.let { if (it is JsonPrimitive) it.content else it.toString() } ?: ""
                    val target = cmd["target"]?.let { if (it is JsonPrimitive) it.content else it.toString() } ?: ""
                    val value = cmd["value"]?.let { if (it is JsonPrimitive) it.content else it.toString() } ?: ""
                    val params = cmd["params"]?.toString().orEmpty()
                    promptBuilder.append("${index + 1}. 操作类型: $action")
                    if (stepName.isNotBlank()) promptBuilder.append(", 步骤名: $stepName")
                    if (target.isNotBlank()) promptBuilder.append(", 目标: $target")
                    if (value.isNotBlank()) promptBuilder.append(", 值: $value")
                    if (params.isNotBlank() && params != "{}") promptBuilder.append(", 参数: $params")
                    promptBuilder.append("\n")
                }
                promptBuilder.toString()
            } else {
                Logger.e(TAG, "Task ${taskDto.id} has atomic track but empty commands")
                null
            }
        } else {
            Logger.e(TAG, "Unknown track: ${taskDto.track} for task ${taskDto.id}")
            null
        }
    }

    private fun startTaskWithRuntime(runtime: RemoteTaskRuntime, taskExecutionManager: TaskExecutionManager) {
        runtime.attemptStartedAt = System.currentTimeMillis()
        runtime.currentStepNo = null
        runtime.stepStartedAt = null
        runtime.latestThinking = null
        runtime.latestScreenshot = null
        runtime.lastUploadedScreenshotDigest = null
        runtime.guardCancelled = false
        runtime.guardCancelReason = null
        val started = taskExecutionManager.startTask(runtime.description, runtime.taskId)
        if (!started) {
            Logger.e(TAG, "Failed to start remote task ${runtime.taskId} at attempt ${runtime.attempt}")
            serviceScope.launch {
                reportSyntheticFail(runtime.taskId, "STEP_PARAM_INVALID", "Task start blocked")
            }
            synchronized(runtimeLock) {
                taskRuntimeMap.remove(runtime.taskId)
                pollPausedByTask = false
            }
            _currentRemoteTask.value = null
        }
    }

    private suspend fun observeTaskEvents() {
        val taskExecutionManager = TaskExecutionManager
        val deviceManager = ComponentManager.getInstance(this).deviceManager
        taskExecutionManager.taskEvents.collectLatest { event ->
            val taskIdLong = event.taskId.toLongOrNull()
            if (taskIdLong == null) {
                Logger.w(TAG, "Skip event report for non-numeric taskId: ${event.taskId}")
                return@collectLatest
            }
            val runtime = synchronized(runtimeLock) { taskRuntimeMap[event.taskId] }
            if (event.type == TaskEventType.STEP_STARTED) {
                val stepNo = (event.data as? Int)
                if (runtime != null) {
                    runtime.currentStepNo = stepNo
                    runtime.stepStartedAt = System.currentTimeMillis()
                }
            }
            if (event.type == TaskEventType.THINKING_UPDATED) {
                runtime?.latestThinking = event.data as? String
            }
            if (event.type == TaskEventType.SCREENSHOT_COMPLETED) {
                val base64 = event.data as? String
                runtime?.latestScreenshot = if (!base64.isNullOrEmpty()) "data:image/webp;base64,$base64" else null
            }

            val status = when (event.type) {
                TaskEventType.COMPLETED -> "SUCCESS"
                TaskEventType.FAILED -> "FAIL"
                else -> "RUNNING"
            }
            val rawErrorMessage = event.data?.toString().orEmpty()
            val guardTimeout = runtime?.guardCancelled == true
            val normalizedErrorMessage = if (guardTimeout) runtime?.guardCancelReason ?: rawErrorMessage else rawErrorMessage
            val errorCode = if (event.type == TaskEventType.FAILED) {
                if (guardTimeout) "MODEL_TIMEOUT" else mapErrorCode(rawErrorMessage)
            } else null
            val durationMs = if (event.type == TaskEventType.FAILED || event.type == TaskEventType.COMPLETED) {
                runtime?.attemptStartedAt?.let { System.currentTimeMillis() - it }
            } else null
            val commandId = runtime?.currentStepNo?.toString()
            val screenshotUrl = runtime?.let { resolveScreenshotUrlForUpload(it) }
            val thinking = runtime?.latestThinking
            val progress = buildStepDecisionProgress(
                runtime = runtime,
                eventType = event.type,
                errorCode = errorCode,
                errorMessage = normalizedErrorMessage,
                guardTimeout = guardTimeout
            )
            val trace = listOf(
                buildJsonObject {
                    put("eventType", event.type.name)
                    put("message", normalizedErrorMessage)
                    put("timestamp", event.timestamp)
                }
            )
            val req = EventReq(
                eventNo = generateEventNo(event.taskId, event.timestamp),
                taskId = taskIdLong,
                commandId = commandId,
                status = status,
                timestamp = event.timestamp,
                durationMs = durationMs,
                screenshotUrl = screenshotUrl,
                errorCode = errorCode,
                errorMessage = if (event.type == TaskEventType.FAILED) normalizedErrorMessage else null,
                trace = trace,
                thinking = thinking,
                sensitiveScreenDetected = detectSensitiveScreen(normalizedErrorMessage),
                progress = progress,
                hmac = null,
            )
            val signedReq = signEventReq(deviceManager.deviceId, req)
            sendEventWithFallback(deviceManager.deviceId, signedReq)
            if (event.type == TaskEventType.COMPLETED) {
                synchronized(runtimeLock) {
                    taskRuntimeMap.remove(event.taskId)
                    if (taskRuntimeMap.isEmpty()) {
                        pollPausedByTask = false
                    }
                }
                if (_currentRemoteTask.value == event.taskId) {
                    _currentRemoteTask.value = null
                }
            } else if (event.type == TaskEventType.FAILED) {
                val shouldRetry = runtime?.let { canRetry(it) } ?: false
                if (shouldRetry && runtime != null) {
                    runtime.attempt += 1
                    runtime.guardCancelled = false
                    runtime.guardCancelReason = null
                    serviceScope.launch {
                        delay(runtime.retryBackoffMs)
                        val expired = runtime.deadlineAtMs?.let { System.currentTimeMillis() > it } ?: false
                        if (expired) {
                            synchronized(runtimeLock) {
                                taskRuntimeMap.remove(runtime.taskId)
                                if (taskRuntimeMap.isEmpty()) {
                                    pollPausedByTask = false
                                }
                            }
                            if (_currentRemoteTask.value == runtime.taskId) {
                                _currentRemoteTask.value = null
                            }
                        } else {
                            startTaskWithRuntime(runtime, TaskExecutionManager)
                        }
                    }
                } else {
                    synchronized(runtimeLock) {
                        taskRuntimeMap.remove(event.taskId)
                        if (taskRuntimeMap.isEmpty()) {
                            pollPausedByTask = false
                        }
                    }
                    if (_currentRemoteTask.value == event.taskId) {
                        _currentRemoteTask.value = null
                    }
                }
            }
        }
    }

    private suspend fun startEventRetryLoop() {
        while (serviceScope.isActive) {
            flushPendingEvents()
            delay(EVENT_RETRY_LOOP_INTERVAL_MS)
        }
    }

    private suspend fun flushPendingEvents() {
        val now = System.currentTimeMillis()
        val ready = synchronized(runtimeLock) {
            pendingEvents.filter { it.nextRetryAt <= now }.take(20)
        }
        if (ready.isEmpty()) {
            return
        }
        var changed = false
        for (queued in ready) {
            val success = sendEventNow(queued.deviceId, queued.payload)
            synchronized(runtimeLock) {
                val index = pendingEvents.indexOfFirst { it.id == queued.id }
                if (index < 0) {
                    return@synchronized
                }
                if (success) {
                    pendingEvents.removeAt(index)
                    changed = true
                } else {
                    val current = pendingEvents[index]
                    val nextRetry = current.retryCount + 1
                    if (nextRetry > EVENT_RETRY_MAX) {
                        pendingEvents.removeAt(index)
                    } else {
                        val multiplier = 1L shl min(nextRetry, 6)
                        pendingEvents[index] = current.copy(
                            retryCount = nextRetry,
                            nextRetryAt = System.currentTimeMillis() + EVENT_RETRY_BASE_BACKOFF_MS * multiplier
                        )
                    }
                    changed = true
                }
            }
        }
        if (changed) {
            synchronized(runtimeLock) {
                eventRetryStore.save(pendingEvents)
            }
        }
    }

    private suspend fun startRuntimeGuardLoop() {
        while (serviceScope.isActive) {
            val currentTaskId = _currentRemoteTask.value
            if (!currentTaskId.isNullOrBlank()) {
                val runtime = synchronized(runtimeLock) { taskRuntimeMap[currentTaskId] }
                if (runtime != null && !runtime.guardCancelled) {
                    val now = System.currentTimeMillis()
                    val deadlineExceeded = runtime.deadlineAtMs?.let { now > it } ?: false
                    val stepTimeoutExceeded = runtime.stepStartedAt?.let { now - it > runtime.stepTimeoutMs } ?: false
                    if (deadlineExceeded) {
                        runtime.guardCancelled = true
                        runtime.guardCancelReason = "Task deadline exceeded"
                        TaskExecutionManager.cancelTask()
                    } else if (stepTimeoutExceeded) {
                        runtime.guardCancelled = true
                        runtime.guardCancelReason = "Step timeout exceeded"
                        TaskExecutionManager.cancelTask()
                    }
                }
            }
            delay(DEADLINE_CHECK_INTERVAL_MS)
        }
    }

    private fun canRetry(runtime: RemoteTaskRuntime): Boolean {
        if (runtime.attempt > runtime.retryMax) {
            return false
        }
        val now = System.currentTimeMillis()
        val deadlineExceeded = runtime.deadlineAtMs?.let { now > it } ?: false
        return !deadlineExceeded
    }

    private fun buildStepDecisionProgress(
        runtime: RemoteTaskRuntime?,
        eventType: TaskEventType,
        errorCode: String?,
        errorMessage: String,
        guardTimeout: Boolean
    ): JsonObject {
        val failureEvent = eventType == TaskEventType.FAILED
        val pageType = resolvePageType(errorMessage, failureEvent)
        val pageSignature = resolvePageSignature(runtime, pageType, errorMessage)
        val targetResolved = resolveTargetResolved(eventType, errorCode, errorMessage)
        val actionResult = resolveActionResult(eventType, errorCode, errorMessage, guardTimeout)
        val recoverable = resolveRecoverable(errorCode, errorMessage, guardTimeout)
        val failureCategory = resolveFailureCategory(errorCode, errorMessage, pageType, guardTimeout)
        return buildJsonObject {
            put("attempt", runtime?.attempt ?: 1)
            put("stepNo", runtime?.currentStepNo ?: -1)
            put("pageType", pageType)
            put("pageSignature", pageSignature)
            put("targetResolved", targetResolved)
            put("actionResult", actionResult)
            put("recoverable", recoverable)
            put("failureCategory", failureCategory)
        }
    }

    private fun mapErrorCode(errorMessage: String): String {
        val msg = errorMessage.lowercase()
        return when {
            msg.contains("shizuku") -> "SHIZUKU_NOT_RUNNING"
            msg.contains("overlay") -> "OVERLAY_NOT_GRANTED"
            msg.contains("keyboard") -> "KEYBOARD_NOT_ENABLED"
            msg.contains("sensitive") -> "SENSITIVE_SCREEN_BLACKOUT"
            msg.contains("connection") || msg.contains("network") || msg.contains("host") -> "API_CONNECTION_FAILED"
            msg.contains("timeout") || msg.contains("超时") -> "MODEL_TIMEOUT"
            msg.contains("unsupported") -> "STEP_ACTION_UNSUPPORTED"
            msg.contains("not found") || msg.contains("找不到") || msg.contains("未找到") -> "ELEMENT_NOT_FOUND"
            msg.contains("param") || msg.contains("invalid") -> "STEP_PARAM_INVALID"
            else -> "STEP_PARAM_INVALID"
        }
    }

    private fun detectSensitiveScreen(errorMessage: String): Boolean {
        val msg = errorMessage.lowercase()
        return msg.contains("sensitive") || msg.contains("隐私") || msg.contains("敏感")
    }

    private fun resolvePageType(errorMessage: String, failureEvent: Boolean): String {
        val msg = errorMessage.lowercase()
        if (detectSensitiveScreen(errorMessage)) {
            return "SENSITIVE_PAGE"
        }
        return when {
            msg.contains("permission") || msg.contains("授权") || msg.contains("权限") -> "PERMISSION_PAGE"
            msg.contains("login") || msg.contains("登录") -> "LOGIN_PAGE"
            msg.contains("popup") || msg.contains("弹窗") || msg.contains("dialog") -> "POPUP_PAGE"
            msg.contains("loading") || msg.contains("加载") -> "LOADING_PAGE"
            msg.contains("search") || msg.contains("搜索") -> "SEARCH_PAGE"
            msg.contains("detail") || msg.contains("详情") -> "DETAIL_PAGE"
            msg.contains("list") || msg.contains("列表") -> "LIST_PAGE"
            msg.contains("home") || msg.contains("首页") -> "HOME_PAGE"
            failureEvent -> "UNKNOWN_PAGE"
            else -> "UNKNOWN_PAGE"
        }
    }

    private fun resolvePageSignature(runtime: RemoteTaskRuntime?, pageType: String, errorMessage: String): String {
        val stepPart = runtime?.currentStepNo?.takeIf { it > 0 }?.let { "step-$it" } ?: "step-unknown"
        val errorPart = when {
            errorMessage.isBlank() -> "normal"
            detectSensitiveScreen(errorMessage) -> "sensitive"
            else -> sanitizeSignatureToken(errorMessage)
        }
        return "$pageType|$stepPart|$errorPart"
    }

    private fun sanitizeSignatureToken(raw: String): String {
        val normalized = raw.lowercase()
            .replace(Regex("[^a-z0-9\u4e00-\u9fa5]+"), "-")
            .trim('-')
        return if (normalized.isBlank()) {
            "unknown"
        } else {
            normalized.take(48)
        }
    }

    private fun resolveTargetResolved(eventType: TaskEventType, errorCode: String?, errorMessage: String): Boolean {
        if (eventType == TaskEventType.COMPLETED || eventType == TaskEventType.ACTION_EXECUTED) {
            return true
        }
        val msg = errorMessage.lowercase()
        if (errorCode == "ELEMENT_NOT_FOUND" || msg.contains("not found") || msg.contains("未找到") || msg.contains("找不到")) {
            return false
        }
        return eventType != TaskEventType.FAILED
    }

    private fun resolveActionResult(
        eventType: TaskEventType,
        errorCode: String?,
        errorMessage: String,
        guardTimeout: Boolean
    ): String {
        if (eventType == TaskEventType.COMPLETED) {
            return "SUCCESS"
        }
        if (eventType == TaskEventType.ACTION_EXECUTED) {
            return "PARTIAL"
        }
        if (eventType != TaskEventType.FAILED) {
            return "UNKNOWN"
        }
        val msg = errorMessage.lowercase()
        return when {
            guardTimeout || errorCode == "MODEL_TIMEOUT" || msg.contains("timeout") || msg.contains("超时") -> "INTERRUPTED"
            errorCode == "ELEMENT_NOT_FOUND" || msg.contains("not found") || msg.contains("未找到") || msg.contains("找不到") -> "TARGET_NOT_FOUND"
            errorCode == "STEP_PARAM_INVALID" || msg.contains("invalid") || msg.contains("param") -> "INVALID_PARAM"
            errorCode == "STEP_ACTION_UNSUPPORTED" || msg.contains("unsupported") -> "BLOCKED"
            errorCode == "SHIZUKU_NOT_RUNNING" || errorCode == "OVERLAY_NOT_GRANTED" || errorCode == "KEYBOARD_NOT_ENABLED" -> "BLOCKED"
            errorCode == "SENSITIVE_SCREEN_BLACKOUT" -> "BLOCKED"
            else -> "NO_EFFECT"
        }
    }

    private fun resolveRecoverable(errorCode: String?, errorMessage: String, guardTimeout: Boolean): Boolean {
        if (guardTimeout) {
            return true
        }
        val msg = errorMessage.lowercase()
        return when (errorCode) {
            "API_CONNECTION_FAILED", "MODEL_TIMEOUT", "ELEMENT_NOT_FOUND" -> true
            "SHIZUKU_NOT_RUNNING", "OVERLAY_NOT_GRANTED", "KEYBOARD_NOT_ENABLED", "SENSITIVE_SCREEN_BLACKOUT" -> false
            else -> {
                !(msg.contains("invalid") || msg.contains("unsupported") || msg.contains("敏感"))
            }
        }
    }

    private fun resolveFailureCategory(
        errorCode: String?,
        errorMessage: String,
        pageType: String,
        guardTimeout: Boolean
    ): String {
        val msg = errorMessage.lowercase()
        if (guardTimeout || errorCode == "MODEL_TIMEOUT" || msg.contains("timeout") || msg.contains("超时")) {
            return "TIMEOUT"
        }
        if (pageType == "SENSITIVE_PAGE" || errorCode == "SENSITIVE_SCREEN_BLACKOUT") {
            return "SENSITIVE_SCREEN"
        }
        return when {
            errorCode == "SHIZUKU_NOT_RUNNING" || errorCode == "OVERLAY_NOT_GRANTED" || errorCode == "KEYBOARD_NOT_ENABLED" || msg.contains("permission") || msg.contains("权限") -> "PERMISSION"
            errorCode == "API_CONNECTION_FAILED" || msg.contains("network") || msg.contains("connection") || msg.contains("host") -> "NETWORK"
            errorCode == "ELEMENT_NOT_FOUND" || msg.contains("not found") || msg.contains("未找到") || msg.contains("找不到") -> "ELEMENT"
            errorCode == "STEP_ACTION_UNSUPPORTED" -> "ACTION_EXECUTION"
            errorCode == "STEP_PARAM_INVALID" || msg.contains("invalid") || msg.contains("param") -> "DATA"
            msg.contains("page") || msg.contains("页面") || pageType != "UNKNOWN_PAGE" -> "PAGE_STATE"
            else -> "UNKNOWN"
        }
    }

    private suspend fun reportSyntheticFail(taskId: String, errorCode: String, message: String) {
        val deviceManager = ComponentManager.getInstance(this).deviceManager
        val taskIdLong = taskId.toLongOrNull() ?: return
        val progress = buildStepDecisionProgress(
            runtime = synchronized(runtimeLock) { taskRuntimeMap[taskId] },
            eventType = TaskEventType.FAILED,
            errorCode = errorCode,
            errorMessage = message,
            guardTimeout = errorCode == "MODEL_TIMEOUT"
        )
        val req = EventReq(
            eventNo = generateEventNo(taskId, System.currentTimeMillis()),
            taskId = taskIdLong,
            status = "FAIL",
            timestamp = System.currentTimeMillis(),
            errorCode = errorCode,
            errorMessage = message,
            trace = listOf(
                buildJsonObject {
                    put("eventType", "SYNTHETIC_FAIL")
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                }
            ),
            progress = progress,
            hmac = null,
        )
        val signedReq = signEventReq(deviceManager.deviceId, req)
        sendEventWithFallback(deviceManager.deviceId, signedReq)
    }

    private suspend fun sendEventWithFallback(deviceId: String, req: EventReq) {
        val success = sendEventNow(deviceId, req)
        if (!success) {
            enqueueEvent(deviceId, req)
        }
    }

    private suspend fun sendEventNow(deviceId: String, req: EventReq): Boolean {
        return try {
            val api = AiMacrodroidApiClient.getInstance(this).api
            val resp = api.reportEvent(deviceId, req)
            if (resp.isSuccess()) {
                true
            } else {
                if (resp.needReRegister()) {
                    val tokenManager = TokenManager.getInstance(this)
                    tokenManager.clearToken()
                    registerDeviceIfNeeded()
                }
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to report event", e)
            false
        }
    }

    private fun enqueueEvent(deviceId: String, req: EventReq) {
        synchronized(runtimeLock) {
            if (pendingEvents.size >= LOCAL_EVENT_QUEUE_MAX_SIZE) {
                pendingEvents.removeAt(0)
            }
            pendingEvents.add(
                QueuedEvent(
                    deviceId = deviceId,
                    payload = req,
                    retryCount = 0,
                    nextRetryAt = System.currentTimeMillis() + EVENT_RETRY_BASE_BACKOFF_MS
                )
            )
            eventRetryStore.save(pendingEvents)
        }
    }

    private fun signEventReq(deviceId: String, req: EventReq): EventReq {
        val key = resolveHmacKey()
        if (key.isBlank()) {
            return req
        }
        val stepId = req.stepId?.toString() ?: req.commandId.orEmpty()
        val bodyDigest = buildBodyDigest(req)
        val dataToSign = "$deviceId:${req.taskId}:$stepId:${req.timestamp}:$bodyDigest"
        val signature = HmacUtil.generateSignature(dataToSign, key).trimEnd('=')
        return req.copy(hmac = signature)
    }

    private fun resolveHmacKey(): String {
        val settingsManager = SettingsManager.getInstance(this)
        val secret = settingsManager.getAiMacrodroidSecretKey()
        if (secret.isNotBlank()) {
            return secret
        }
        return TokenManager.getInstance(this).getToken().orEmpty()
    }

    private fun buildBodyDigest(req: EventReq): String {
        val body = buildJsonObject {
            put("eventNo", JsonPrimitive(req.eventNo))
            put("taskId", JsonPrimitive(req.taskId))
            put("stepId", req.stepId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("commandId", req.commandId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("status", JsonPrimitive(req.status))
            put("timestamp", JsonPrimitive(req.timestamp))
            put("durationMs", req.durationMs?.let { JsonPrimitive(it) } ?: JsonNull)
            put("screenshotUrl", req.screenshotUrl?.let { JsonPrimitive(it) } ?: JsonNull)
            put("foregroundPkg", req.foregroundPkg?.let { JsonPrimitive(it) } ?: JsonNull)
            put("elements", req.elements?.let { JsonArray(it) } ?: JsonNull)
            put("errorCode", req.errorCode?.let { JsonPrimitive(it) } ?: JsonNull)
            put("errorMessage", req.errorMessage?.let { JsonPrimitive(it) } ?: JsonNull)
            put("trace", req.trace?.let { JsonArray(it) } ?: JsonNull)
            put("thinking", req.thinking?.let { JsonPrimitive(it) } ?: JsonNull)
            put("sensitiveScreenDetected", JsonPrimitive(req.sensitiveScreenDetected))
            put("progress", req.progress ?: JsonNull)
        }
        return sha256Hex(body.toString())
    }

    private fun generateEventNo(taskId: String, timestamp: Long): String {
        val suffix = System.nanoTime().toString().takeLast(6)
        return "EV-$taskId-$timestamp-$suffix"
    }

    private fun sha256Hex(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun resolveScreenshotUrlForUpload(runtime: RemoteTaskRuntime): String? {
        val current = runtime.latestScreenshot ?: return null
        val digest = screenshotDigest(current)
        if (digest == null || digest == runtime.lastUploadedScreenshotDigest) {
            return null
        }
        runtime.lastUploadedScreenshotDigest = digest
        return current
    }

    private fun screenshotDigest(screenshotUrl: String): String? {
        val payload = screenshotUrl.substringAfter(',', "").trim()
        if (payload.isBlank()) {
            return null
        }
        return sha256Hex(payload)
    }

    private fun readLong(obj: JsonObject?, key: String): Long? {
        return obj?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    }

    private fun readInt(obj: JsonObject?, key: String): Int? {
        return obj?.get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    }

    private fun resolveDeadlineAt(taskDto: TaskDto): Long? {
        val deadlineRaw = readLong(taskDto.constraints, "deadlineMs") ?: return null
        val now = System.currentTimeMillis()
        return if (deadlineRaw > now) {
            deadlineRaw
        } else {
            now + deadlineRaw
        }
    }

    private fun resolveStepTimeoutFromCommands(taskDto: TaskDto): Long? {
        val commands = taskDto.commands ?: return null
        var maxTimeout: Long? = null
        for (cmd in commands) {
            val timeout = cmd["timeoutMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
            if (timeout > 0 && (maxTimeout == null || timeout > maxTimeout)) {
                maxTimeout = timeout
            }
        }
        return maxTimeout
    }

    private fun createNotification(): Notification {
        val channelId = "aimacrodroid_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AiMacrodroid Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection with AiMacrodroid Server"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AiMacrodroid Service")
            .setContentText("Connected to AiMacrodroid Server")
            .setSmallIcon(R.drawable.ic_link)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

}
