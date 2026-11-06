package com.skycast.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Open-Meteo client (free, no API key). */
object WeatherApi {

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "SkyCast-Android")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(
            httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json")
        )
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val region = listOf(o.optString("admin1", ""), o.optString("country", ""))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            out.add(Place(o.getString("name"), region, o.getDouble("latitude"), o.getDouble("longitude")))
        }
        out
    }

    suspend fun fetch(place: Place): WeatherData = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
            "weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day,uv_index" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"
        val root = JSONObject(httpGet(url))

        val cur = root.getJSONObject("current")
        val current = CurrentWeather(
            temp = cur.getDouble("temperature_2m"),
            feels = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            humidity = cur.optInt("relative_humidity_2m"),
            windKmh = cur.optDouble("wind_speed_10m", 0.0),
            windDir = cur.optInt("wind_direction_10m"),
            pressure = cur.optDouble("pressure_msl", 1013.0),
            cloud = cur.optInt("cloud_cover"),
            precip = cur.optDouble("precipitation", 0.0),
            code = cur.optInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
        )
        val nowHour = cur.optString("time", "").take(13)

        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        var start = 0
        for (i in 0 until times.length()) {
            if (times.getString(i).startsWith(nowHour)) { start = i; break }
        }
        val temps = h.getJSONArray("temperature_2m")
        val probs = h.getJSONArray("precipitation_probability")
        val codes = h.getJSONArray("weather_code")
        val day = h.getJSONArray("is_day")
        val uv = h.getJSONArray("uv_index")
        val hours = ArrayList<HourPoint>()
        val end = minOf(times.length(), start + 24)
        for (i in start until end) {
            hours.add(
                HourPoint(
                    label = if (i == start) "Now" else hourLabel(times.getString(i)),
                    temp = temps.optDouble(i, 0.0),
                    code = codes.optInt(i),
                    precipProb = probs.optInt(i),
                    isDay = day.optInt(i, 1) == 1,
                )
            )
        }

        val d = root.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val dCode = d.getJSONArray("weather_code")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dRise = d.getJSONArray("sunrise")
        val dSet = d.getJSONArray("sunset")
        val dProb = d.getJSONArray("precipitation_probability_max")
        val days = ArrayList<DayPoint>()
        for (i in 0 until dTimes.length()) {
            days.add(
                DayPoint(
                    label = if (i == 0) "Today" else dayLabel(dTimes.getString(i)),
                    code = dCode.optInt(i),
                    max = dMax.optDouble(i, 0.0),
                    min = dMin.optDouble(i, 0.0),
                    precipProb = dProb.optInt(i),
                    sunrise = clock(dRise.optString(i, "")),
                    sunset = clock(dSet.optString(i, "")),
                )
            )
        }

        val air = try {
            val a = JSONObject(
                httpGet(
                    "https://air-quality-api.open-meteo.com/v1/air-quality" +
                        "?latitude=${place.lat}&longitude=${place.lon}&current=us_aqi,pm2_5"
                )
            ).getJSONObject("current")
            AirQuality(a.optInt("us_aqi"), a.optDouble("pm2_5", 0.0))
        } catch (e: Exception) {
            null
        }

        WeatherData(
            place = place,
            current = current,
            hours = hours,
            days = days,
            air = air,
            uvNow = uv.optDouble(start, 0.0),
        )
    }

    private fun hourLabel(iso: String): String = try {
        LocalTime.parse(iso.substring(11, 16)).format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))
    } catch (e: Exception) {
        iso.takeLast(5)
    }

    private fun clock(iso: String): String = try {
        LocalTime.parse(iso.substring(11, 16)).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    } catch (e: Exception) {
        "--"
    }

    private fun dayLabel(date: String): String = try {
        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    } catch (e: Exception) {
        date
    }
}
