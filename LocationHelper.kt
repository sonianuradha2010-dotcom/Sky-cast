package com.skycast.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    /** Best-effort current location; caller must hold a location permission. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        val last = providers
            .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000L) return last

        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return last
        }
        val fresh = withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    lm, provider, signal, ContextCompat.getMainExecutor(ctx)
                ) { loc -> if (cont.isActive) cont.resume(loc) }
            }
        }
        return fresh ?: last
    }

    /** Returns (name, region) using the platform Geocoder when available. */
    @Suppress("DEPRECATION")
    suspend fun describe(ctx: Context, loc: Location): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                if (a != null) {
                    val name = a.locality ?: a.subAdminArea ?: a.adminArea ?: "My location"
                    val region = listOfNotNull(a.adminArea, a.countryName).filter { it != name }.joinToString(", ")
                    return@withContext name to region
                }
            }
        } catch (e: Exception) {
            // fall through
        }
        "My location" to "%.2f, %.2f".format(Locale.US, loc.latitude, loc.longitude)
    }
}
