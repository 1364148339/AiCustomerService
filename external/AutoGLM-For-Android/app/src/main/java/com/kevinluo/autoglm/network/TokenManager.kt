package com.kevinluo.autoglm.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Token 管理工具类，用于安全存储和获取设备注册后的 Token。
 */
class TokenManager private constructor(context: Context) {

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "aimacrodroid_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        securePrefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return securePrefs.getString(KEY_DEVICE_TOKEN, null)
    }

    fun clearToken() {
        securePrefs.edit().remove(KEY_DEVICE_TOKEN).apply()
    }

    companion object {
        private const val KEY_DEVICE_TOKEN = "device_token"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager(context).also { instance = it }
            }
        }
    }
}
