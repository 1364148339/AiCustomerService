package com.kevinluo.autoglm.task

import com.kevinluo.autoglm.action.AgentAction

data class TaskEvent(
    val taskId: String,
    val type: TaskEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Any? = null
)

enum class TaskEventType {
    STARTED,
    STEP_STARTED,
    THINKING_UPDATED,
    ACTION_EXECUTED,
    COMPLETED,
    FAILED,
    SCREENSHOT_STARTED,
    SCREENSHOT_COMPLETED
}