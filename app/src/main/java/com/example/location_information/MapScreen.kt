package com.example.location_information

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.location_information.getAddressFromLatLng
import com.example.location_information.getCurrentLocation
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import androidx.lifecycle.compose.LocalLifecycleOwner

// Data class for custom markers
data class CustomMarker(
    val position: LatLng,
    val title: String,
    val address: String = ""
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var userAddress by remember { mutableStateOf("Fetching address...") }
    var customMarkers by remember { mutableStateOf<List<CustomMarker>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedMarkerInfo by remember { mutableStateOf<String?>(null) }

    //initial location showing
    val defaultLocation = LatLng(40.7128, -74.0060)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Fetch user location once permission granted
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (!locationPermissions.permissions.all { it.status.isGranted }) {
            isLoading = true
            val location = getCurrentLocation(context)
            if (location != null) {
                userLocation = location
                userAddress = getAddressFromLatLng(context, location)
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(location, 16f),
                    durationMs = 1000
                )
            } else {
                userAddress = "Could not determine location"
            }
            isLoading = false
        }
    }

    // Re-check location when app resumes (e.g. after granting from Settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (locationPermissions.permissions.all { it.status.isGranted }) {
                    scope.launch {
                        isLoading = true
                        val location = getCurrentLocation(context)
                        if (location != null) {
                            userLocation = location
                            userAddress = getAddressFromLatLng(context, location)
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(location, 16f),
                                durationMs = 1000
                            )
                        } else {
                            userAddress = "Could not determine location"
                        }
                        isLoading = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!locationPermissions.allPermissionsGranted) {
        PermissionRequestScreen(
            onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() },
            permissionDenied = locationPermissions.permissions.any {
                !it.status.isGranted && !it.status.shouldShowRationale
            }
        )
        return
    }

    // --- Main Map UI ---
    Box(modifier = Modifier.fillMaxSize()) {

        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false, // We use our own FAB
                zoomControlsEnabled = false
            ),
            onMapClick = { latLng ->
                // Place a custom marker on tap
                scope.launch {
                    val address = getAddressFromLatLng(context, latLng)
                    val markerNumber = customMarkers.size + 1
                    customMarkers = customMarkers + CustomMarker(
                        position = latLng,
                        title = "Pin #$markerNumber",
                        address = address
                    )
                }
            }
        ) {
            // User location marker
            userLocation?.let { loc ->
                Marker(
                    state = MarkerState(position = loc),
                    title = "You are here",
                    snippet = userAddress,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    onClick = {
                        selectedMarkerInfo = "📍 Your Location\n$userAddress"
                        false
                    }
                )
            }

            customMarkers.forEachIndexed { index, marker ->
                Marker(
                    state = MarkerState(position = marker.position),
                    title = marker.title,
                    snippet = marker.address,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    onClick = {
                        selectedMarkerInfo = "📌 ${marker.title}\n${marker.address}"
                        false
                    }
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        //address info card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            selectedMarkerInfo?.let { info ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = info,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { selectedMarkerInfo = null }) {
                            Text("✕")
                        }
                    }
                }
            }

            // Your location address card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = userAddress,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customMarkers.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "${customMarkers.size} pins",
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                scope.launch {
                    userLocation?.let {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(it, 16f),
                            durationMs = 800
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 160.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Center on my location"
            )
        }

        // Clear pins button
        if (customMarkers.isNotEmpty()) {
            FloatingActionButton(
                onClick = { customMarkers = emptyList(); selectedMarkerInfo = null },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 230.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error
            ) {
                Text("✕", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    permissionDenied: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Location Access Needed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (permissionDenied)
                        "Location permission was denied. Please enable it in your device Settings to use the map."
                    else
                        "This app needs your location to show where you are on the map and display your address.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                if (!permissionDenied) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Location Permission")
                    }
                } else {
                    Text(
                        text = "Open Settings → Apps → Your App → Permissions → Location",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}