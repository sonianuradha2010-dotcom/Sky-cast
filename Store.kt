package com.skycast.weather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Tiny SharedPreferences wrapper for saved places and settings. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("skycast", Context.MODE_PRIVATE)

    var fahrenheit: Boolean
        get() = prefs.getBoolean("fahrenheit", false)
        set(v) = prefs.edit().putBoolean("fahrenheit", v).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean("soundOn", false)
        set(v) = prefs.edit().putBoolean("soundOn", v).apply()

    var volume: Float
        get() = prefs.getFloat("volume", 0.6f)
        set(v) = prefs.edit().putFloat("volume", v).apply()

    var soundMode: String
        get() = prefs.getString("soundMode", "AUTO") ?: "AUTO"
        set(v) = prefs.edit().putString("soundMode", v).apply()

    var selected: Int
        get() = prefs.getInt("selected", 0)
        set(v) = prefs.edit().putInt("selected", v).apply()

    var places: List<Place>
        get() {
            val raw = prefs.getString("places", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Place(o.getString("name"), o.optString("region", ""), o.getDouble("lat"), o.getDouble("lon"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("name", p.name)
                        .put("region", p.region)
                        .put("lat", p.lat)
                        .put("lon", p.lon)
                )
            }
            prefs.edit().putString("places", arr.toString()).apply()
        }
}
