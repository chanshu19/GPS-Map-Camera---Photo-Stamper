package com.ckstudio.cameragps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Data class holding all location information needed for the banner overlay.
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val fullAddress: String,
    val plusCode: String,
    val latString: String,
    val longString: String,
    val countryCode: String,
    val timestamp: Long,
    val formattedDateTime: String
)

/**
 * Helper class for fetching GPS location, performing reverse geocoding,
 * and generating location metadata for the banner.
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData {
        val location = suspendCancellableCoroutine { cont ->
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { loc ->
                if (loc != null) {
                    cont.resume(loc)
                } else {
                    // Fallback: try last known location
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            cont.resume(lastLoc)
                        } else {
                            cont.resumeWithException(Exception("Location unavailable. Please enable GPS."))
                        }
                    }.addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
                }
            }.addOnFailureListener { e ->
                cont.resumeWithException(e)
            }

            cont.invokeOnCancellation {
                cancellationToken.cancel()
            }
        }

        val lat = location.latitude
        val lng = location.longitude

        return getLocationData(lat, lng)
    }

    suspend fun getLocationData(lat: Double, lng: Double, date: Date = Date()): LocationData = withContext(Dispatchers.IO) {
        // Reverse geocode
        var cityName = ""
        var stateName = ""
        var countryName = ""
        var countryCode = ""
        var postalCode = ""
        var subLocality = ""

        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                cityName = addr.locality ?: addr.subAdminArea ?: ""
                stateName = addr.adminArea ?: ""
                countryName = addr.countryName ?: ""
                countryCode = addr.countryCode ?: ""
                postalCode = addr.postalCode ?: ""
                subLocality = addr.subLocality ?: addr.featureName ?: cityName
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Generate plus code
        val fullPlusCode = PlusCodeEncoder.encode(lat, lng).lowercase(Locale.ENGLISH)
        val shortPlusCode = shortenPlusCode(fullPlusCode)

        // Build location name: "Gokhulpur, Bihar, India"
        val locationName = buildString {
            val primary = if (subLocality.isNotEmpty() && subLocality != cityName) subLocality else cityName
            if (primary.isNotEmpty()) append(primary)
            if (stateName.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(stateName)
            }
            if (countryName.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(countryName)
            }
        }

        // Build full address: "2q3m+7x9, Gokhulpur, Bihar 821108, India"
        val fullAddress = buildString {
            append(shortPlusCode)
            val locality = if (subLocality.isNotEmpty()) subLocality else cityName
            if (locality.isNotEmpty()) append(", $locality")
            if (cityName.isNotEmpty() && cityName != locality) append(", $cityName")
            if (stateName.isNotEmpty()) {
                append(", $stateName")
                if (postalCode.isNotEmpty()) append(" $postalCode")
            }
            if (countryName.isNotEmpty()) append(", $countryName")
        }

        // Format lat/long strings
        val latStr = String.format(Locale.ENGLISH, "%.6f°", lat)
        val lngStr = String.format(Locale.ENGLISH, "%.6f°", lng)

        // Format date/time with Hindi day name
        val hindiDay = SimpleDateFormat("EEEE", Locale("hi", "IN")).format(date)
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(date)
        val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(date)
        val tzStr = SimpleDateFormat("XXX", Locale.ENGLISH).format(date)
        val formattedDateTime = "$hindiDay, $dateStr $timeStr GMT $tzStr"

        LocationData(
            latitude = lat,
            longitude = lng,
            locationName = locationName.ifEmpty { "Unknown Location" },
            fullAddress = fullAddress.ifEmpty { shortPlusCode },
            plusCode = shortPlusCode,
            latString = latStr,
            longString = lngStr,
            countryCode = countryCode.ifEmpty { "IN" },
            timestamp = date.time,
            formattedDateTime = formattedDateTime
        )
    }

    /**
     * Shortens a full plus code by removing the first 2 pairs (4 chars)
     * that can be recovered from locality context.
     * E.g., "7mqf2q3m+7x9" → "2q3m+7x9"
     */
    private fun shortenPlusCode(fullCode: String): String {
        val withoutPlus = fullCode.replace("+", "")
        if (withoutPlus.length >= 8) {
            val shortened = withoutPlus.substring(4)
            return if (shortened.length > 4) {
                shortened.substring(0, 4) + "+" + shortened.substring(4)
            } else {
                "$shortened+"
            }
        }
        return fullCode
    }
}

/**
 * Generates Open Location Codes (Plus Codes) from latitude/longitude.
 * See: https://plus.codes/
 */
object PlusCodeEncoder {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"

    fun encode(latitude: Double, longitude: Double): String {
        var lat = latitude.coerceIn(-90.0, 90.0) + 90.0
        var lng = longitude
        while (lng < -180.0) lng += 360.0
        while (lng >= 180.0) lng -= 360.0
        lng += 180.0

        val code = CharArray(10)
        var latRes = 9.0   // 180.0 / 20
        var lngRes = 18.0  // 360.0 / 20

        for (i in 0 until 5) {
            val latIdx = min((lat / latRes).toInt(), 19)
            val lngIdx = min((lng / lngRes).toInt(), 19)
            code[i * 2] = ALPHABET[latIdx]
            code[i * 2 + 1] = ALPHABET[lngIdx]
            lat -= latIdx * latRes
            lng -= lngIdx * lngRes
            latRes /= 20.0
            lngRes /= 20.0
        }

        val codeStr = String(code)
        return codeStr.substring(0, 8) + "+" + codeStr.substring(8)
    }
}
