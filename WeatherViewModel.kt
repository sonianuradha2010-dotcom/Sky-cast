package com.skycast.weather

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Empty : UiState
    data object Loading : UiState
    data class Error(val msg: String) : UiState
    data class Ready(val data: WeatherData) : UiState
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    val engine = AmbientEngine()

    var places by mutableStateOf(store.places)
        private set
    var selected by mutableIntStateOf(0)
        private set
    var state by mutableStateOf<UiState>(UiState.Empty)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var fahrenheit by mutableStateOf(store.fahrenheit)
        private set

    var soundOn by mutableStateOf(store.soundOn)
        private set
    var volume by mutableFloatStateOf(store.volume)
        private set
    var soundMode by mutableStateOf(
        runCatching { SoundMode.valueOf(store.soundMode) }.getOrDefault(SoundMode.AUTO)
    )
        private set

    var results by mutableStateOf<List<Place>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var locating by mutableStateOf(false)
        private set

    /** One-shot message shown in a snackbar, then cleared by the UI. */
    var message by mutableStateOf<String?>(null)

    private var foreground = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        selected = store.selected.coerceIn(0, maxOf(0, places.size - 1))
        load(isRefresh = false)
    }

    // ---------------------------------------------------------------- weather

    fun load(isRefresh: Boolean) {
        val place = places.getOrNull(selected)
        if (place == null) {
            state = UiState.Empty
            applySound()
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (isRefresh && state is UiState.Ready) refreshing = true else state = UiState.Loading
            try {
                state = UiState.Ready(WeatherApi.fetch(place))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isRefresh && state is UiState.Ready) {
                    message = "Couldn't refresh. Check your connection."
                } else {
                    state = UiState.Error("Couldn't load the forecast. Check your connection and try again.")
                }
            } finally {
                refreshing = false
            }
            applySound()
        }
    }

    fun select(index: Int) {
        if (index !in places.indices) return
        selected = index
        store.selected = index
        load(isRefresh = false)
    }

    fun addPlace(p: Place) {
        val existing = places.indexOfFirst { it.key() == p.key() }
        if (existing >= 0) {
            selected = existing
        } else {
            places = places + p
            selected = places.lastIndex
            store.places = places
        }
        store.selected = selected
        results = emptyList()
        load(isRefresh = false)
    }

    fun removePlace(index: Int) {
        if (index !in places.indices) return
        val current = selected
        places = places.toMutableList().also { it.removeAt(index) }
        store.places = places
        selected = when {
            places.isEmpty() -> 0
            index < current -> current - 1
            current >= places.size -> places.lastIndex
            else -> current
        }
        store.selected = selected
        load(isRefresh = false)
    }

    fun setFahrenheit(v: Boolean) {
        fahrenheit = v
        store.fahrenheit = v
    }

    // ----------------------------------------------------------------- search

    fun search(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            searching = true
            try {
                results = WeatherApi.search(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
                message = "Search failed. Check your connection."
            } finally {
                searching = false
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun useLocation() {
        if (!hasLocationPermission() || locating) return
        viewModelScope.launch {
            locating = true
            try {
                val app = getApplication<Application>()
                val loc = LocationHelper.current(app)
                if (loc == null) {
                    message = "Couldn't get your location. Is location turned on?"
                } else {
                    val (name, region) = LocationHelper.describe(app, loc)
                    addPlace(Place(name, region, loc.latitude, loc.longitude))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't get your location."
            } finally {
                locating = false
            }
        }
    }

    // ------------------------------------------------------------------ sound

    fun toggleSound() {
        soundOn = !soundOn
        store.soundOn = soundOn
        applySound()
    }

    fun pickMode(mode: SoundMode) {
        soundMode = mode
        store.soundMode = mode.name
        if (!soundOn) {
            soundOn = true
            store.soundOn = true
        }
        applySound()
    }

    fun updateVolume(v: Float) {
        volume = v
        engine.volume = v
    }

    fun saveVolume() {
        store.volume = volume
    }

    fun setForeground(fg: Boolean) {
        foreground = fg
        applySound()
    }

    /** The scene currently selected (Auto follows the live weather). */
    fun currentScene(): Scene {
        if (soundMode != SoundMode.AUTO) return soundMode.scene
        val d = (state as? UiState.Ready)?.data ?: return Scene.SILENT
        return sceneFor(d.current.code, d.current.isDay, d.current.windKmh)
    }

    private fun applySound() {
        val scene = currentScene()
        engine.target = scene
        engine.volume = volume
        if (soundOn && foreground && !scene.isSilent) engine.start() else engine.stop()
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
