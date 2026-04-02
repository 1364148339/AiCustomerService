package com.kevinluo.autoglm.network

import android.content.Context
import com.kevinluo.autoglm.network.model.EventReq
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class QueuedEvent(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val payload: EventReq,
    val retryCount: Int = 0,
    val nextRetryAt: Long = System.currentTimeMillis()
)

class EventRetryStore(context: Context) {
    private val prefs = context.getSharedPreferences("aimacrodroid_event_queue", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val key = "queued_events"

    fun load(): MutableList<QueuedEvent> {
        val raw = prefs.getString(key, null) ?: return mutableListOf()
        return runCatching {
            json.decodeFromString<List<QueuedEvent>>(raw).toMutableList()
        }.getOrElse {
            mutableListOf()
        }
    }

    fun save(events: List<QueuedEvent>) {
        val raw = json.encodeToString(events)
        prefs.edit().putString(key, raw).apply()
    }
}
