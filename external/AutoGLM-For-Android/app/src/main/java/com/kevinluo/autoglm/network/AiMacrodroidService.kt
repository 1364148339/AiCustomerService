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
                if (hasInFlightTask) {
                    Logger.d(TAG, "Remote task in-flight, skipping poll")
                } else if (taskExecutionManager.canStartTask()) {
                    Logger.d(TAG, "Polling for tasks...")
                    val tasks = AiMacrodroidApiClient.getInstance(this).api.pollTasks(deviceManager.deviceId).data.orEmpty()
                    if (tasks.isNotEmpty()) {
                        Logger.i(TAG, "Received ${tasks.size} tasks")
                        val taskToExecute = tasks.firstOrNull()
                        if (taskToExecute != null) {
                            executeRemoteTask(taskToExecute, taskExecutionManager)
                        }
                    }
                } else {
                    Logger.d(TAG, "Cannot start task currently, skipping poll")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Task polling failed", e)
            }

            delay(POLL_TASK_INTERVAL_MS)
        }
    }

    private fun executeRemoteTask(taskDto: TaskDto, taskExecutionManager: TaskExecutionManager) {
        Logger.i(TAG, "Executing remote task: ${taskDto.id} (track: ${taskDto.track})")
        val description = buildTaskDescription(taskDto)
        if (description.isNullOrBlank()) {
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
            val errorCode = if (event.type == TaskEventType.FAILED) {
                if (guardTimeout) "MODEL_TIMEOUT" else mapErrorCode(rawErrorMessage)
            } else null
            val durationMs = if (event.type == TaskEventType.FAILED || event.type == TaskEventType.COMPLETED) {
                runtime?.attemptStartedAt?.let { System.currentTimeMillis() - it }
            } else null
            val commandId = runtime?.currentStepNo?.toString()
            val screenshotUrl = runtime?.latestScreenshot
            val thinking = runtime?.latestThinking
            val progress = buildJsonObject {
                put("attempt", runtime?.attempt ?: 1)
                put("stepNo", runtime?.currentStepNo ?: -1)
            }
            val trace = listOf(
                buildJsonObject {
                    put("eventType", event.type.name)
                    put("message", if (guardTimeout) runtime?.guardCancelReason ?: rawErrorMessage else rawErrorMessage)
                    put("timestamp", event.timestamp)
                }
            )
            val req = EventReq(
                taskId = taskIdLong,
                commandId = commandId,
                status = status,
                timestamp = event.timestamp,
                durationMs = durationMs,
                screenshotUrl = screenshotUrl,
                errorCode = errorCode,
                trace = trace,
                thinking = thinking,
                sensitiveScreenDetected = detectSensitiveScreen(rawErrorMessage),
                progress = progress,
                hmac = null,
            )
            val signedReq = signEventReq(deviceManager.deviceId, req)
            sendEventWithFallback(deviceManager.deviceId, signedReq)
            if (event.type == TaskEventType.COMPLETED) {
                synchronized(runtimeLock) {
                    taskRuntimeMap.remove(event.taskId)
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
            msg.contains("param") || msg.contains("invalid") -> "STEP_PARAM_INVALID"
            else -> "STEP_PARAM_INVALID"
        }
    }

    private fun detectSensitiveScreen(errorMessage: String): Boolean {
        val msg = errorMessage.lowercase()
        return msg.contains("sensitive") || msg.contains("隐私") || msg.contains("敏感")
    }

    private suspend fun reportSyntheticFail(taskId: String, errorCode: String, message: String) {
        val deviceManager = ComponentManager.getInstance(this).deviceManager
        val taskIdLong = taskId.toLongOrNull() ?: return
        val req = EventReq(
            taskId = taskIdLong,
            status = "FAIL",
            timestamp = System.currentTimeMillis(),
            errorCode = errorCode,
            trace = listOf(
                buildJsonObject {
                    put("eventType", "SYNTHETIC_FAIL")
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                }
            ),
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
        val stepId = req.commandId ?: ""
        val bodyDigest = buildBodyDigest(req)
        val dataToSign = "$deviceId:${req.taskId}:$stepId:${req.timestamp}:$bodyDigest"
        val signature = HmacUtil.generateSignature(dataToSign, key)
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
        val body = listOf(
            req.status,
            req.durationMs?.toString().orEmpty(),
            req.screenshotUrl.orEmpty(),
            req.errorCode.orEmpty(),
            req.trace?.joinToString("|") { it.toString() }.orEmpty(),
            req.thinking.orEmpty(),
            req.sensitiveScreenDetected.toString(),
            req.progress?.toString().orEmpty(),
        ).joinToString("#")
        return sha256Hex(body)
    }

    private fun sha256Hex(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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
