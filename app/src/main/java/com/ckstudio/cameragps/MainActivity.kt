package com.ckstudio.cameragps

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ckstudio.cameragps.ui.theme.CameraGPSTheme
import com.ckstudio.cameragps.ui.theme.DarkBackground
import com.ckstudio.cameragps.ui.theme.DarkSurface
import com.ckstudio.cameragps.ui.theme.GreenAccent
import com.ckstudio.cameragps.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraGPSTheme {
                GPSCameraApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GPSCameraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var locationData by remember { mutableStateOf<LocationData?>(null) }
    var mapTileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val locationHelper = remember { LocationHelper(context) }

    // Camera photo URI holder
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    fun createImageUri(): Uri {
        val photoDir = File(context.cacheDir, "camera_photos")
        if (!photoDir.exists()) photoDir.mkdirs()
        val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    // Shared logic to process a photo (load bitmap + fetch location)
    fun processPhoto(uri: Uri) {
        isLoading = true
        loadingMessage = "Loading image..."
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                capturedBitmap = bitmap

                loadingMessage = "Fetching GPS location..."
                try {
                    val locData = locationHelper.getCurrentLocation()
                    locationData = locData

                    // Download real map tile in background
                    loadingMessage = "Loading map..."
                    val tile = withContext(Dispatchers.IO) {
                        ImageStamper.downloadMapTile(locData.latitude, locData.longitude)
                    }
                    mapTileBitmap = tile
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "⚠ Location error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoading = false
                loadingMessage = ""
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoUri?.let { processPhoto(it) }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processPhoto(it) }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                context,
                "Please grant all permissions for full functionality",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        val needed = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            @Suppress("DEPRECATION")
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val notGranted = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("GPS Map Camera", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GreenPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            when {
                // Loading state
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = GreenAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            loadingMessage,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }

                // Preview with banner (image + location available)
                capturedBitmap != null && locationData != null -> {
                    PreviewScreen(
                        bitmap = capturedBitmap!!,
                        locationData = locationData!!,
                        mapTileBitmap = mapTileBitmap,
                        isSaving = isSaving,
                        onRetake = {
                            capturedBitmap = null
                            locationData = null
                            mapTileBitmap = null
                        },
                        onSave = { updatedData, lScale, rScale, bScale ->
                            isSaving = true
                            scope.launch {
                                val stamped = withContext(Dispatchers.Default) {
                                    ImageStamper.stampBanner(
                                        originalBitmap = capturedBitmap!!,
                                        locationData = updatedData,
                                        countryFlag = countryCodeToFlag(updatedData.countryCode),
                                        mapTileBitmap = mapTileBitmap,
                                        leftScale = lScale,
                                        rightScale = rScale,
                                        bottomScale = bScale
                                    )
                                }
                                val fileName = "GPS_${
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)
                                        .format(Date())
                                }.jpg"
                                val saved = withContext(Dispatchers.IO) {
                                    ImageStamper.saveToGallery(context, stamped, fileName)
                                }
                                isSaving = false
                                Toast.makeText(
                                    context,
                                    if (saved) "✅ Saved to Gallery/GPS Camera!" else "❌ Failed to save",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                // Image loaded but no location
                capturedBitmap != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Captured photo",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF332B00)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "⚠️ GPS location not available.\nPlease enable Location Services and try again.",
                                color = Color(0xFFFFB74D),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                capturedBitmap = null
                                locationData = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("↩ Try Again", fontSize = 16.sp)
                        }
                    }
                }

                // Home screen — capture options
                else -> {
                    HomeScreen(
                        onTakePhoto = {
                            val uri = createImageUri()
                            cameraPhotoUri = uri
                            cameraLauncher.launch(uri)
                        },
                        onPickFromGallery = {
                            galleryLauncher.launch("image/*")
                        }
                    )
                }
            }
        }
    }
}

// ─── Home Screen ────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GreenAccent, GreenPrimary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("📍", fontSize = 44.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "GPS Map Camera",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Stamp your photos with GPS location",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(48.dp))

        // Camera button
        Button(
            onClick = onTakePhoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Text("📷  Take Photo", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        // Gallery button
        OutlinedButton(
            onClick = onPickFromGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Text("🖼️  Choose from Gallery", fontSize = 18.sp)
        }

        Spacer(Modifier.height(32.dp))

        // Info text
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "How it works:",
                    color = GreenAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. Take a photo or pick from gallery\n" +
                            "2. Your GPS location is auto-detected\n" +
                            "3. Location banner is overlaid on the photo\n" +
                            "4. Save the stamped photo to gallery",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ─── Preview Screen ─────────────────────────────────────────────────────────────

@Composable
fun PreviewScreen(
    bitmap: Bitmap,
    locationData: LocationData,
    mapTileBitmap: Bitmap?,
    isSaving: Boolean,
    onRetake: () -> Unit,
    onSave: (updatedLocationData: LocationData, leftMargin: Float, rightMargin: Float, bottomMargin: Float) -> Unit
) {
    val context = LocalContext.current
    var currentData by remember { mutableStateOf(locationData) }
    var rawLat by remember { mutableStateOf(locationData.latitude.toString()) }
    var rawLng by remember { mutableStateOf(locationData.longitude.toString()) }
    var selectedDate by remember { mutableStateOf(Date(locationData.timestamp)) }

    var leftMarginScale by remember { mutableFloatStateOf(1f) }
    var rightMarginScale by remember { mutableFloatStateOf(1f) }
    var bottomMarginScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(rawLat, rawLng, selectedDate) {
        val lat = rawLat.toDoubleOrNull()
        val lng = rawLng.toDoubleOrNull()
        if (lat != null && lng != null) {
            delay(1000) // Debounce
            val newData = LocationHelper(context).getLocationData(lat, lng, selectedDate)
            currentData = newData
        }
    }

    val calendar = Calendar.getInstance().apply { time = selectedDate }
    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            selectedDate = calendar.time
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
    )
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Image with banner overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LocationBannerOverlay(
                bitmap = bitmap,
                locationData = currentData,
                mapTileBitmap = mapTileBitmap,
                leftMarginScale = leftMarginScale,
                rightMarginScale = rightMarginScale,
                bottomMarginScale = bottomMarginScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Edit controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Left Margin (${"%.1f".format(leftMarginScale)}x)", color = Color.White, fontSize = 12.sp)
            Slider(
                value = leftMarginScale,
                onValueChange = { leftMarginScale = it },
                valueRange = 0f..3f
            )

            Text("Right Margin (${"%.1f".format(rightMarginScale)}x)", color = Color.White, fontSize = 12.sp)
            Slider(
                value = rightMarginScale,
                onValueChange = { rightMarginScale = it },
                valueRange = 0f..3f
            )

            Text("Bottom Margin (${"%.1f".format(bottomMarginScale)}x)", color = Color.White, fontSize = 12.sp)
            Slider(
                value = bottomMarginScale,
                onValueChange = { bottomMarginScale = it },
                valueRange = 0f..3f
            )
            OutlinedTextField(
                value = rawLat,
                onValueChange = { rawLat = it },
                label = { Text("Latitude") },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GreenAccent,
                    unfocusedBorderColor = Color.White,
                    focusedLabelColor = GreenAccent,
                    unfocusedLabelColor = Color.White
                )
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = rawLng,
                onValueChange = { rawLng = it },
                label = { Text("Longitude") },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GreenAccent,
                    unfocusedBorderColor = Color.White,
                    focusedLabelColor = GreenAccent,
                    unfocusedLabelColor = Color.White
                )
            )
            Spacer(Modifier.height(4.dp))
            Box {
                OutlinedTextField(
                    value = currentData.formattedDateTime,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Date & Time") },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GreenAccent,
                        unfocusedBorderColor = Color.White,
                        focusedLabelColor = GreenAccent,
                        unfocusedLabelColor = Color.White
                    )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { datePickerDialog.show() }
                )
            }
        }

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Retake button
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("↩ Retake", fontSize = 15.sp)
            }

            // Save button
            Button(
                onClick = { onSave(currentData, leftMarginScale, rightMarginScale, bottomMarginScale) },
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1.5f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...", fontSize = 15.sp)
                } else {
                    Text("💾 Save to Gallery", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Utility ────────────────────────────────────────────────────────────────────

/**
 * Converts a 2-letter ISO country code to its flag emoji.
 * E.g., "IN" → 🇮🇳, "US" → 🇺🇸
 */
fun countryCodeToFlag(countryCode: String): String {
    if (countryCode.length != 2) return "🏳️"
    val first = Character.toChars(
        countryCode[0].uppercaseChar().code - 'A'.code + 0x1F1E6
    )
    val second = Character.toChars(
        countryCode[1].uppercaseChar().code - 'A'.code + 0x1F1E6
    )
    return String(first) + String(second)
}