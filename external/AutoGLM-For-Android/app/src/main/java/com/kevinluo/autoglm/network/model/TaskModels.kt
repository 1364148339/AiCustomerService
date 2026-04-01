package com.kevinluo.autoglm.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TaskDto(
    val id: String,
    val type: String,
    val track: String,
    val intent: String? = null,
    val commands: List<JsonObject>? = null,
    val constraints: JsonObject? = null,
    val successCriteria: JsonObject? = null,
    val observability: JsonObject? = null,
    val safetyRails: JsonObject? = null,
    val rhythm: JsonObject? = null,
    val loop: JsonObject? = null,
    val retryPolicy: JsonObject? = null,
    val priority: Int = 0
)

@Serializable
data class EventReq(
    val taskId: String,
    val commandId: String? = null,
    val status: String, // RUNNING, SUCCESS, FAIL
    val timestamp: Long,
    val durationMs: Long? = null,
    val screenshotUrl: String? = null,
    val errorCode: String? = null,
    val trace: List<JsonObject>? = null,
    val thinking: String? = null,
    val sensitiveScreenDetected: Boolean = false,
    val progress: JsonObject? = null,
    val hmac: String? = null
)

@Serializable
data class EventResp(
    val ok: Boolean
)
