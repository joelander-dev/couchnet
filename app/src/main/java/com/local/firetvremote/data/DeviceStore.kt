package com.local.firetvremote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedDevice(
    val name: String,
    val host: String,
    val token: String,
)

class DeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<SavedDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val host = item.optString("host")
                    val token = item.optString("token")
                    val name = item.optString("name").ifBlank { "Fire TV" }
                    if (host.isNotBlank() && token.isNotBlank()) {
                        add(SavedDevice(name = name, host = host, token = token))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(devices: List<SavedDevice>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("name", device.name)
                    .put("host", device.host)
                    .put("token", device.token),
            )
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    fun lastHost(): String? = prefs.getString(KEY_LAST, null)

    fun setLastHost(host: String) {
        prefs.edit().putString(KEY_LAST, host).apply()
    }

    companion object {
        private const val PREFS = "fire_remote"
        private const val KEY_DEVICES = "devices"
        private const val KEY_LAST = "last_host"
    }
}
