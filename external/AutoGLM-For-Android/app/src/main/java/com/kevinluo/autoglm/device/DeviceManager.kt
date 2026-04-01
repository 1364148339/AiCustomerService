package com.kevinluo.autoglm.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.kevinluo.autoglm.ComponentManager
import java.util.UUID

/**
 * Manages and collects device information.
 */
class DeviceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("device_manager_prefs", Context.MODE_PRIVATE)

    /**
     * Unique identifier for this device.
     */
    val deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }

    val brand: String
        get() = Build.BRAND

    val model: String
        get() = Build.MODEL

    val androidVersion: String
        get() = Build.VERSION.RELEASE

    fun getResolution(): String {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return "${metrics.widthPixels}x${metrics.heightPixels}"
    }

    fun getBatteryPct(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
    }

    fun isCharging(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "NONE"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    fun isShizukuAvailable(): Boolean {
        return ComponentManager.getInstance(context).isServiceConnected
    }

    fun isOverlayGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isKeyboardEnabled(): Boolean {
        val enabledImeList = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        return enabledImeList?.contains(context.packageName) == true
    }

    fun getCapabilities(): List<String> {
        return listOf("vision", "touch", "keyboard")
    }

    suspend fun getForegroundPkg(): String {
        val deviceExecutor = ComponentManager.getInstance(context).deviceExecutor
        return deviceExecutor?.getCurrentApp() ?: ""
    }
}
