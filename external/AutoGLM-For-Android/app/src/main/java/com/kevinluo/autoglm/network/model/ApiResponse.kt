package com.kevinluo.autoglm.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ApiResponse<T>(
    val code: JsonElement,
    val message: String,
    val data: T? = null
) {
    fun codeText(): String {
        return code.jsonPrimitive.content.trim().uppercase()
    }

    fun isSuccess(): Boolean {
        val raw = codeText()
        return raw == "OK" || raw == "200"
    }

    fun isUnauthorized(): Boolean {
        val raw = codeText()
        return raw == "UNAUTHORIZED" || raw == "401"
    }

    fun needReRegister(): Boolean {
        val raw = codeText()
        return raw == "DEVICE_NOT_FOUND" || raw == "SIGNATURE_INVALID" || raw == "SIGNATURE_EXPIRED" || raw == "UNAUTHORIZED" || raw == "401"
    }
}
