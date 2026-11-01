package app.betar.comm.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import app.betar.comm.messaging.GeoPoint
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A one-shot device location read using the platform's own `android.location.LocationManager`,
 * deliberately not `FusedLocationProviderClient`: that is a Google Play Services API, and this
 * project avoids Play Services dependencies throughout for F-Droid distribution reasons
 * (`docs/DISTRIBUTION.md`, and the same reasoning already stated against ML Kit's barcode
 * scanner in `app/build.gradle.kts`). The framework API costs a little more code (provider
 * selection, an SDK-version branch) in exchange for not pulling in a dependency this project has
 * already ruled out elsewhere.
 *
 * Single-shot only, by design: there is no repeating-update path anywhere in this object.
 * Continuous location tracking is this project's own stated highest-risk feature and is not
 * built (`docs/THREAT-MODEL.md`); every call site that wants a location gets exactly one
 * coordinate pair for that one moment, never a stream.
 */
object LocationHelper {

    /** Returns null if location permission isn't granted, no provider is available, or the read
     *  times out/fails -- callers must treat a null result as "couldn't get a location," not an
     *  error worth crashing over, same honest-degradation pattern every other optional-hardware
     *  path in this codebase already follows (e.g. `WifiDirectTransportDriver.kt`). */
    suspend fun getOneShotLocation(context: Context): GeoPoint? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = selectProvider(locationManager) ?: return null

        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCurrentLocationModern(locationManager, provider)
        } else {
            requestSingleUpdateLegacy(locationManager, provider)
        }
        return location?.let { GeoPoint(latitude = it.latitude, longitude = it.longitude) }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Prefers GPS for accuracy, falls back to the network provider, returns null if this
     *  device/build has neither enabled (common on an emulator with no location set). */
    private fun selectProvider(locationManager: LocationManager): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    @SuppressLint("MissingPermission") // checked via hasLocationPermission before either read path is called
    private suspend fun getCurrentLocationModern(locationManager: LocationManager, provider: String): Location? =
        suspendCoroutine { continuation ->
            try {
                locationManager.getCurrentLocation(
                    provider,
                    CancellationSignal(),
                    Executors.newSingleThreadExecutor(),
                ) { location -> continuation.resume(location) }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }

    @Suppress("DEPRECATION") // requestSingleUpdate is the only one-shot API before API 30
    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdateLegacy(locationManager: LocationManager, provider: String): Location? =
        suspendCoroutine { continuation ->
            var resumed = false
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(location)
                    }
                }
            }
            try {
                locationManager.requestSingleUpdate(provider, listener, null)
            } catch (e: Exception) {
                if (!resumed) {
                    resumed = true
                    continuation.resume(null)
                }
            }
        }
}

// Restored 2026-11-01: a separate, concurrently-run pass on this same file overwrote this
// standard geohash encoder with a different pass's unrelated `LocationHelper` rewrite, both
// targeting this one path with the `Write` tool rather than an in-place edit. Re-added from
// the original pass's own description of what it built, rather than left silently missing,
// since `FeedTab.kt`'s composer already calls it below.

private const val GEOHASH_BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

/**
 * The standard Gustavo Niemeyer geohash algorithm: interleave binary-searched latitude and
 * longitude bits, five bits at a time, into base32 characters. Public domain, the same
 * algorithm every geohash library implements, hand-rolled here rather than adding a dependency
 * for roughly 40 lines of well-understood, easily-checked logic.
 *
 * Sanity check (the standard "ezs42" reference example): `encodeGeohash(42.6, -5.6, 5)` should
 * read "ezs42", the worked example geohash.org and most geohash library test suites use.
 */
fun encodeGeohash(latitude: Double, longitude: Double, precision: Int): String {
    var latMin = -90.0
    var latMax = 90.0
    var lonMin = -180.0
    var lonMax = 180.0
    val result = StringBuilder()
    var isEven = true
    var bit = 0
    var charBits = 0

    while (result.length < precision) {
        if (isEven) {
            val mid = (lonMin + lonMax) / 2
            if (longitude >= mid) {
                charBits = charBits or (1 shl (4 - bit))
                lonMin = mid
            } else {
                lonMax = mid
            }
        } else {
            val mid = (latMin + latMax) / 2
            if (latitude >= mid) {
                charBits = charBits or (1 shl (4 - bit))
                latMin = mid
            } else {
                latMax = mid
            }
        }
        isEven = !isEven
        if (bit < 4) {
            bit++
        } else {
            result.append(GEOHASH_BASE32[charBits])
            bit = 0
            charBits = 0
        }
    }
    return result.toString()
}
