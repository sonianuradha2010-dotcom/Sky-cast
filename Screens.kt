@file:OptIn(ExperimentalMaterial3Api::class)

package com.skycast.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skycast.weather.DayPoint
import com.skycast.weather.HourPoint
import com.skycast.weather.SoundMode
import com.skycast.weather.UiState
import com.skycast.weather.WeatherData
import com.skycast.weather.WeatherViewModel
import com.skycast.weather.aqiLabel
import com.skycast.weather.describe
import com.skycast.weather.humidityLabel
import com.skycast.weather.iconKind
import com.skycast.weather.title
import com.skycast.weather.uvLabel
import com.skycast.weather.windDirLabel
import kotlin.math.roundToInt

private fun temp(c: Double, f: Boolean): String = "${(if (f) c * 9.0 / 5.0 + 32.0 else c).roundToInt()}°"
private fun wind(kmh: Double, f: Boolean): String =
    if (f) "${(kmh * 0.621371).roundToInt()} mph" else "${kmh.roundToInt()} km/h"

@Composable
fun SkyCastApp(vm: WeatherViewModel, onRequestLocation: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    val place = vm.places.getOrNull(vm.selected)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            place?.name ?: "SkyCast",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (place != null && place.region.isNotBlank()) {
                            Text(
                                place.region,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { sheetOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search and manage places")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = vm.state) {
                UiState.Empty -> Welcome(vm.locating, onRequestLocation) { sheetOpen = true }
                UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is UiState.Error -> ErrorView(s.msg) { vm.load(isRefresh = false) }
                is UiState.Ready -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.load(isRefresh = true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    WeatherContent(vm, s.data)
                }
            }
        }
    }

    if (sheetOpen) {
        PlaceSheet(vm, onRequestLocation) { sheetOpen = false }
    }
}

@Composable
private fun Welcome(locating: Boolean, onLocate: () -> Unit, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WeatherIcon(com.skycast.weather.IconKind.PARTLY, true, Modifier.size(140.dp), animate = true)
        Spacer(Modifier.height(16.dp))
        Text("Welcome to SkyCast", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Live forecasts with a soundscape that matches the sky.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onLocate, enabled = !locating) {
            if (locating) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Use my location")
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search for a city")
        }
    }
}

@Composable
private fun ErrorView(msg: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WeatherIcon(com.skycast.weather.IconKind.CLOUDY, true, Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text(msg, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun WeatherContent(vm: WeatherViewModel, d: WeatherData) {
    val f = vm.fahrenheit
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeroCard(d, f) }
        item { SoundCard(vm) }
        item { HourlyCard(d.hours, f) }
        item { DailyCard(d.days, f) }
        item { DetailsGrid(d, f) }
        item {
            Text(
                "Weather data by Open-Meteo.com",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------ hero

@Composable
private fun HeroCard(d: WeatherData, f: Boolean) {
    val c = d.current
    val today = d.days.firstOrNull()
    val kind = iconKind(c.code)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            WeatherParticles(kind, Modifier.matchParentSize())
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(temp(c.temp, f), style = MaterialTheme.typography.displayLarge)
                        Text(describe(c.code), style = MaterialTheme.typography.titleLarge)
                    }
                    WeatherIcon(kind, c.isDay, Modifier.size(104.dp), animate = true)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (today != null) {
                        Pill("H ${temp(today.max, f)}")
                        Pill("L ${temp(today.min, f)}")
                    }
                    Pill("Feels ${temp(c.feels, f)}")
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ----------------------------------------------------------------- sound

@Composable
private fun SoundCard(vm: WeatherViewModel) {
    val scene = vm.currentScene()
    val subtitle = when {
        !vm.soundOn -> "Off"
        vm.soundMode == SoundMode.AUTO -> "Auto · ${scene.title()}"
        else -> vm.soundMode.label
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ambient sound", style = MaterialTheme.typography.labelLarge)
                    Text(subtitle, style = MaterialTheme.typography.titleLarge)
                }
                FilledIconButton(onClick = { vm.toggleSound() }) {
                    PlayPauseIcon(playing = vm.soundOn)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔈")
                Slider(
                    value = vm.volume,
                    onValueChange = { vm.updateVolume(it) },
                    onValueChangeFinished = { vm.saveVolume() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("🔊")
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundMode.entries.forEach { m ->
                    FilterChip(
                        selected = vm.soundMode == m,
                        onClick = { vm.pickMode(m) },
                        label = { Text("${m.emoji} ${m.label}") },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hourly

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun HourlyCard(hours: List<HourPoint>, f: Boolean) {
    SectionCard("Next 24 hours") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(hours) { h ->
                Column(
                    Modifier.width(60.dp).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(h.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    WeatherIcon(iconKind(h.code), h.isDay, Modifier.size(36.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(temp(h.temp, f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (h.precipProb >= 20) "${h.precipProb}%" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- daily

@Composable
private fun DailyCard(days: List<DayPoint>, f: Boolean) {
    val weekMin = days.minOfOrNull { it.min } ?: 0.0
    val weekMax = days.maxOfOrNull { it.max } ?: 1.0
    val range = (weekMax - weekMin).coerceAtLeast(1.0)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    SectionCard("7-day forecast") {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.label, Modifier.width(52.dp), style = MaterialTheme.typography.bodyLarge)
                    WeatherIcon(iconKind(day.code), true, Modifier.size(32.dp))
                    Text(
                        if (day.precipProb >= 20) "${day.precipProb}%" else "",
                        Modifier.width(44.dp).padding(start = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        temp(day.min, f),
                        Modifier.width(38.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                    val start = ((day.min - weekMin) / range).toFloat()
                    val end = ((day.max - weekMin) / range).toFloat()
                    Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 8.dp)) {
                        val r = CornerRadius(size.height / 2f)
                        drawRoundRect(track, Offset.Zero, size, r)
                        val w = ((end - start) * size.width).coerceAtLeast(size.height)
                        val x = (start * size.width).coerceAtMost(size.width - w)
                        drawRoundRect(fill, Offset(x, 0f), Size(w, size.height), r)
                    }
                    Text(
                        temp(day.max, f),
                        Modifier.width(38.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- details

@Composable
private fun DetailCard(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailsGrid(d: WeatherData, f: Boolean) {
    val c = d.current
    val today = d.days.firstOrNull()
    val cells = listOf(
        Cell("Feels like", temp(c.feels, f), "Actual ${temp(c.temp, f)}"),
        Cell("Humidity", "${c.humidity}%", humidityLabel(c.humidity)),
        Cell("Wind", wind(c.windKmh, f), "From ${windDirLabel(c.windDir)}"),
        Cell("Pressure", "${c.pressure.roundToInt()}", "hPa · sea level"),
        Cell("UV index", d.uvNow.roundToInt().toString(), uvLabel(d.uvNow)),
        Cell("Air quality", d.air?.aqi?.toString() ?: "--", d.air?.let { aqiLabel(it.aqi) } ?: "Unavailable"),
        Cell("Sunrise", today?.sunrise ?: "--", "Today"),
        Cell("Sunset", today?.sunset ?: "--", "Today"),
        Cell("Cloud cover", "${c.cloud}%", if (c.cloud < 25) "Mostly clear" else if (c.cloud < 70) "Partly covered" else "Overcast"),
        Cell("Precipitation", String.format(java.util.Locale.US, "%.1f", c.precip), "mm right now"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { DetailCard(it.title, it.value, it.sub, Modifier.weight(1f)) }
            }
        }
    }
}

private data class Cell(val title: String, val value: String, val sub: String)
