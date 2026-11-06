package com.skycast.weather

data class Place(val name: String, val region: String, val lat: Double, val lon: Double) {
    fun key(): String = "%.2f,%.2f".format(java.util.Locale.US, lat, lon)
}

data class CurrentWeather(
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val windKmh: Double,
    val windDir: Int,
    val pressure: Double,
    val cloud: Int,
    val precip: Double,
    val code: Int,
    val isDay: Boolean,
)

data class HourPoint(
    val label: String,
    val temp: Double,
    val code: Int,
    val precipProb: Int,
    val isDay: Boolean,
)

data class DayPoint(
    val label: String,
    val code: Int,
    val max: Double,
    val min: Double,
    val precipProb: Int,
    val sunrise: String,
    val sunset: String,
)

data class AirQuality(val aqi: Int, val pm25: Double)

data class WeatherData(
    val place: Place,
    val current: CurrentWeather,
    val hours: List<HourPoint>,
    val days: List<DayPoint>,
    val air: AirQuality?,
    val uvNow: Double,
)

enum class IconKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

fun iconKind(code: Int): IconKind = when (code) {
    0, 1 -> IconKind.CLEAR
    2 -> IconKind.PARTLY
    3 -> IconKind.CLOUDY
    45, 48 -> IconKind.FOG
    51, 53, 55, 56, 57 -> IconKind.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> IconKind.RAIN
    71, 73, 75, 77, 85, 86 -> IconKind.SNOW
    95, 96, 99 -> IconKind.STORM
    else -> IconKind.CLOUDY
}

fun describe(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51 -> "Light drizzle"
    53 -> "Drizzle"
    55 -> "Heavy drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80 -> "Rain showers"
    81 -> "Heavy showers"
    82 -> "Violent showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Unknown"
}

fun windDirLabel(deg: Int): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg % 360) + 360 + 22) / 45) % 8]
}

fun uvLabel(uv: Double): String = when {
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very high"
    else -> "Extreme"
}

fun aqiLabel(aqi: Int): String = when {
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy for some"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

fun humidityLabel(h: Int): String = when {
    h < 30 -> "Dry"
    h < 60 -> "Comfortable"
    h < 80 -> "Humid"
    else -> "Very humid"
}
