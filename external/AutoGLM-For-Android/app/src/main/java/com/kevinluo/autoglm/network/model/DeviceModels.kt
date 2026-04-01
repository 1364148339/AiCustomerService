package com.kevinluo.autoglm.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterReq(
    val deviceId: String,
    val brand: String,
    val model: String,
    val androidVersion: String,
    val resolution: String,
    val shizukuAvailable: Boolean,
    val overlayGranted: Boolean,
    val keyboardEnabled: Boolean,
    val capabilities: List<String>
)

@Serializable
data class DeviceRegisterResp(
    val registered: Boolean,
    val token: String
)

@Serializable
data class HeartbeatReq(
    val deviceId: String,
    val foregroundPkg: String,
    val batteryPct: Int,
    val networkType: String,
    val charging: Boolean,
    val shizukuAvailable: Boolean,
    val overlayGranted: Boolean,
    val keyboardEnabled: Boolean,
    val capabilities: List<String>,
    val sseSupported: Boolean
)

@Serializable
data class HeartbeatResp(
    val ok: Boolean
)
