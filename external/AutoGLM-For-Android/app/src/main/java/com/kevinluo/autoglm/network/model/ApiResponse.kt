package com.kevinluo.autoglm.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Long,
    val message: String,
    val data: T? = null
)
