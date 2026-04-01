package com.kevinluo.autoglm.network

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kevinluo.autoglm.settings.SettingsManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Client for AiMacrodroid API.
 */
class AiMacrodroidApiClient private constructor(private val context: Context) {

    private val settingsManager = SettingsManager.getInstance(context)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        // Get the base URL from settings
        val baseUrl = settingsManager.getAiMacrodroidBaseUrl()
        if (baseUrl.isBlank()) {
            return@Interceptor chain.proceed(originalRequest)
        }

        // Get the Secret Key from settings
        val secretKey = settingsManager.getAiMacrodroidSecretKey()
        
        // Create request builder
        val requestBuilder = originalRequest.newBuilder()
        
        // Add Authorization header with HMAC signature if secret key is available
        if (secretKey.isNotBlank()) {
            val timestamp = System.currentTimeMillis().toString()
            val method = originalRequest.method
            val path = originalRequest.url.encodedPath
            
            // Signature logic: method + path + timestamp
            val dataToSign = "$method$path$timestamp"
            val signature = HmacUtil.generateSignature(
                data = dataToSign,
                key = secretKey
            )
            
            requestBuilder.header("Authorization", "HMAC $signature")
            requestBuilder.header("X-Timestamp", timestamp)
        }

        // Add Device Token if available
        val token = TokenManager.getInstance(context).getToken()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("X-Device-Token", token)
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentBaseUrl: String = ""
    private var retrofit: Retrofit? = null

    val api: AiMacrodroidApi
        get() {
            val baseUrl = settingsManager.getAiMacrodroidBaseUrl().let {
                if (it.isNotBlank() && !it.endsWith("/")) "$it/" else it
            }
            
            if (baseUrl.isBlank()) {
                throw IllegalStateException("AiMacrodroid Base URL is not configured")
            }

            if (retrofit == null || currentBaseUrl != baseUrl) {
                currentBaseUrl = baseUrl
                retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
            }
            
            return retrofit!!.create(AiMacrodroidApi::class.java)
        }

    companion object {
        @Volatile
        private var instance: AiMacrodroidApiClient? = null

        fun getInstance(context: Context): AiMacrodroidApiClient {
            return instance ?: synchronized(this) {
                instance ?: AiMacrodroidApiClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
