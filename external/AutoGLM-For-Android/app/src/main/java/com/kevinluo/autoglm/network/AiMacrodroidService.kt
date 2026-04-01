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
import com.kevinluo.autoglm.task.TaskEvent
import com.kevinluo.autoglm.task.TaskEventType
import com.kevinluo.autoglm.task.TaskExecutionManager
import com.kevinluo.autoglm.action.AgentAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import com.kevinluo.autoglm.util.ErrorHandler
import com.kevinluo.autoglm.util.AutoGLMError
import com.kevinluo.autoglm.util.ErrorType
import com.kevinluo.autoglm.network.HmacUtil
import com.kevinluo.autoglm.network.TokenManager
import com.kevinluo.autoglm.network.AiMacrodroidApiClient
import com.kevinluo.autoglm.network.model.DeviceRegisterReq
import com.kevinluo.autoglm.network.model.HeartbeatReq
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val heartbeatIntervalMs = 30_000L // 30 seconds
    private val pollTaskIntervalMs = 5_000L // 5 seconds

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "AiMacrodroidService created")
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "AiMacrodroidService started")
        
        serviceScope.launch {
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
        
        serviceScope.launch {
            try {
                startTaskPollLoop()
            } catch (e: Exception) {
                Logger.e(TAG, "Error in AiMacrodroidService poll task loop", e)
            }
        }
        
        serviceScope.launch {
            try {
                observeTaskEvents()
            } catch (e: Exception) {
                Logger.e(TAG, "Error observing task events", e)
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
            if (resp.registered) {
                tokenManager.saveToken(resp.token)
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
            return false
        }
    }

    private suspend fun startHeartbeatLoop() {
        val componentManager = ComponentManager.getInstance(this)
        val deviceManager = componentManager.deviceManager
        val settingsManager = componentManager.settingsManager

        while (serviceScope.isActive) {
            if (settingsManager.getAiMacrodroidBaseUrl().isBlank()) {
                delay(heartbeatIntervalMs)
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
                if (resp.ok) {
                    Logger.d(TAG, "Heartbeat ok")
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    Logger.w(TAG, "Heartbeat returned not ok")
                    _connectionState.value = ConnectionState.ERROR
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Heartbeat failed", e)
                _connectionState.value = ConnectionState.ERROR
            }

            delay(heartbeatIntervalMs)
        }
    }

    private suspend fun startTaskPollLoop() {
        val componentManager = ComponentManager.getInstance(this)
        val deviceManager = componentManager.deviceManager
        val settingsManager = componentManager.settingsManager
        val taskExecutionManager = com.kevinluo.autoglm.task.TaskExecutionManager

        while (serviceScope.isActive) {
            if (settingsManager.getAiMacrodroidBaseUrl().isBlank()) {
                delay(pollTaskIntervalMs)
                continue
            }

            try {
                // Only poll if we can start a task
                if (taskExecutionManager.canStartTask()) {
                    Logger.d(TAG, "Polling for tasks...")
                    val tasks = AiMacrodroidApiClient.getInstance(this).api.pollTasks(deviceManager.deviceId)
                    if (tasks.isNotEmpty()) {
                        Logger.i(TAG, "Received ${tasks.size} tasks")
                        val taskToExecute = tasks.firstOrNull() // Pick the first task with highest priority (assuming sorted by server or we could sort here)
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

            delay(pollTaskIntervalMs)
        }
    }

    private fun executeRemoteTask(taskDto: com.kevinluo.autoglm.network.model.TaskDto, taskExecutionManager: com.kevinluo.autoglm.task.TaskExecutionManager) {
        Logger.i(TAG, "Executing remote task: ${taskDto.id} (track: ${taskDto.track})")
        _currentRemoteTask.value = taskDto.id
        if (taskDto.track == "intent" || taskDto.track == "intent-based") {
            val intentStr = taskDto.intent
            if (!intentStr.isNullOrBlank()) {
                taskExecutionManager.startTask(intentStr, taskDto.id)
            } else {
                Logger.e(TAG, "Task ${taskDto.id} has intent track but empty intent string")
            }
        } else if (taskDto.track == "atomic") {
            // Phase 1: Convert atomic commands to a prompt if possible, or execute them directly
            // For now, if we can build a prompt from commands, we run it via agent.
            // A more robust implementation would parse `taskDto.commands` to `AgentAction` list.
            val commands = taskDto.commands
            if (!commands.isNullOrEmpty()) {
                val promptBuilder = StringBuilder("请按顺序执行以下操作：\n")
                commands.forEachIndexed { index, cmd ->
                    val action = cmd["action"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: "unknown"
                    val target = cmd["target"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: ""
                    val value = cmd["value"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: ""
                    promptBuilder.append("${index + 1}. 操作类型: $action")
                    if (target.isNotBlank()) promptBuilder.append(", 目标: $target")
                    if (value.isNotBlank()) promptBuilder.append(", 值: $value")
                    promptBuilder.append("\n")
                }
                taskExecutionManager.startTask(promptBuilder.toString(), taskDto.id)
            } else {
                Logger.e(TAG, "Task ${taskDto.id} has atomic track but empty commands")
            }
        } else {
            Logger.e(TAG, "Unknown track: ${taskDto.track} for task ${taskDto.id}")
        }
    }

    private suspend fun observeTaskEvents() {
        val taskExecutionManager = TaskExecutionManager
        val deviceManager = ComponentManager.getInstance(this).deviceManager
        val tokenManager = TokenManager.getInstance(this)

        taskExecutionManager.taskEvents.collectLatest { event ->
            val status = when (event.type) {
                TaskEventType.COMPLETED -> {
                    if (_currentRemoteTask.value == event.taskId) _currentRemoteTask.value = null
                    "SUCCESS"
                }
                TaskEventType.FAILED -> {
                    if (_currentRemoteTask.value == event.taskId) _currentRemoteTask.value = null
                    "FAIL"
                }
                else -> "RUNNING"
            }
            
            val deviceId = deviceManager.deviceId
            var errorCode: String? = null
            var screenshotUrl: String? = null
            var thinking: String? = null
            
            if (event.type == TaskEventType.FAILED) {
                val errorMsg = event.data?.toString() ?: ""
                errorCode = if (errorMsg.contains("Shizuku", ignoreCase = true)) {
                    "SHIZUKU_NOT_RUNNING"
                } else if (errorMsg.contains("timeout", ignoreCase = true) || errorMsg.contains("超时")) {
                    "MODEL_TIMEOUT"
                } else if (errorMsg.contains("permission", ignoreCase = true)) {
                    "PERMISSION_DENIED"
                } else {
                    "UNKNOWN_ERROR"
                }
            } else if (event.type == TaskEventType.SCREENSHOT_COMPLETED) {
                // Temporary use base64 or empty string as we don't have OSS yet
                val base64 = event.data as? String
                screenshotUrl = if (!base64.isNullOrEmpty()) "data:image/webp;base64,$base64" else null
            } else if (event.type == TaskEventType.THINKING_UPDATED) {
                thinking = event.data as? String
            }
            
            val req = EventReq(
                taskId = event.taskId,
                status = status,
                timestamp = event.timestamp,
                screenshotUrl = screenshotUrl,
                errorCode = errorCode,
                thinking = thinking,
                hmac = "placeholder" // Phase 1: can be empty or calculated
            )
            
            // Calculate HMAC
            val token = tokenManager.getToken()
            val reqWithHmac = if (!token.isNullOrEmpty()) {
                val dataToSign = "${req.taskId}:${req.status}:${req.timestamp}"
                val signature = HmacUtil.generateSignature(dataToSign, token)
                req.copy(hmac = signature)
            } else {
                req
            }

            try {
                Logger.d(TAG, "Reporting event: ${event.type} for task ${event.taskId}")
                AiMacrodroidApiClient.getInstance(this@AiMacrodroidService).api.reportEvent(deviceId, reqWithHmac)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to report event", e)
            }
        }
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

    companion object {
        private const val TAG = "AiMacrodroidService"
        private const val NOTIFICATION_ID = 2001
        
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
        }
    }
}
