package com.example.location_information

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): LatLng? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()

    return suspendCancellableCoroutine { cont ->
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                cont.resume(LatLng(location.latitude, location.longitude))
            } else {
                cont.resume(null)
            }
        }.addOnFailureListener {
            cont.resume(null)
        }

        cont.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }
    }
}

suspend fun getAddressFromLatLng(context: Context, latLng: LatLng): String {
    return suspendCancellableCoroutine { cont ->
        val geocoder = Geocoder(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                cont.resume(addresses.formatAddress())
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = try {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            } catch (e: Exception) {
                null
            }
            cont.resume(addresses.formatAddress())
        }
    }
}

private fun List<Address>?.formatAddress(): String {
    if (isNullOrEmpty()) return "Address not found"
    val address = first()
    return buildString {
        // Street address
        if (address.getAddressLine(0) != null) {
            append(address.getAddressLine(0))
        } else {
            val parts = listOfNotNull(
                address.subThoroughfare,
                address.thoroughfare,
                address.locality,
                address.adminArea,
                address.postalCode,
                address.countryName
            )
            append(parts.joinToString(", "))
        }
    }.ifEmpty { "Lat: ${first().latitude}, Lng: ${first().longitude}" }
}