package com.kevinluo.autoglm.network

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC 签名工具类，用于事件回传时对数据进行签名。
 */
object HmacUtil {

    private const val ALGORITHM = "HmacSHA256"

    /**
     * 使用给定的密钥对数据进行 HMAC-SHA256 签名，并返回 Base64 编码的字符串。
     *
     * @param data 要签名的数据（例如请求的 JSON body 或 timestamp + body）
     * @param key  签名密钥（例如设备的 Token 或 Secret）
     * @return Base64 编码的签名字符串
     */
    fun generateSignature(data: String, key: String): String {
        try {
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), ALGORITHM)
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
