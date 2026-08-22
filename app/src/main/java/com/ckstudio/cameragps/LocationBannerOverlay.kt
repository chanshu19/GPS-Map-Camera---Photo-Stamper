package com.ckstudio.cameragps

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable that displays a captured photo with a GPS location banner overlaid
 * at the bottom, matching the reference design.
 */
@Composable
fun LocationBannerOverlay(
    bitmap: Bitmap,
    locationData: LocationData,
    mapTileBitmap: Bitmap? = null,
    leftMarginScale: Float = 1f,
    rightMarginScale: Float = 1f,
    bottomMarginScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val flag = countryCodeToFlag(locationData.countryCode)

    // --- Configure your margins here ---
    val leftMargin = 32.dp * leftMarginScale
    val rightMargin = 32.dp * rightMarginScale
    val bottomMargin = 34.dp * bottomMarginScale

    Box(modifier = modifier) {
        // Captured photo
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Banner overlay at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = leftMargin,
                    end = rightMargin,
                    bottom = bottomMargin
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            // Map thumbnail — real satellite tile or placeholder
            Box(
                modifier = Modifier
                    .size(105.dp) // Dynamic height/width based on scale
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Gray)
            ) {
                if (mapTileBitmap != null) {
                    Image(
                        bitmap = mapTileBitmap.asImageBitmap(),
                        contentDescription = "Map",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    MapThumbnailComposable()
                }
                
                // "Google" logo overlay with black border
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
                    // Black stroke (border)
                    Text(
                        text = "Google",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle.Default.copy(
                            drawStyle = Stroke(
                                miter = 10f,
                                width = 4f,
                                join = StrokeJoin.Round
                            )
                        )
                    )
                    // White fill
                    Text(
                        text = "Google",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Text column containing branding and location details
            Column(modifier = Modifier.weight(1f)) {
                
                // GPS Map Camera branding (top-right, outside background)
                Text(
                    text = "\uD83D\uDDFA GPS Map Camera",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .align(Alignment.End)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Text information with its own dark background
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.Black.copy(alpha = 0.65f), // Lower opacity for better look
                            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp, topEnd = 0.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 24.dp), // Dynamic vertical padding
                    verticalArrangement = Arrangement.Center // Vertically center the text
                ) {

                Spacer(Modifier.height(4.dp))

                // Location name with flag
                Text(
                    text = "${locationData.locationName} $flag",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Full address with plus code
                Text(
                    text = locationData.fullAddress,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                // Coordinates
                Text(
                    text = "Lat ${locationData.latString} Long ${locationData.longString}",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                // Date and time
                Text(
                    text = locationData.formattedDateTime,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                )
            }
            } // Close wrapper column
        }
    }
}

/**
 * Draws a satellite-style map thumbnail placeholder using Compose Canvas.
 * Shows a green terrain-like background with grid lines and a red location pin.
 */
@Composable
fun MapThumbnailComposable(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Green gradient background (satellite-like)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF225522),
                    Color(0xFF327832),
                    Color(0xFF1E4B28),
                    Color(0xFF2D6437)
                ),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Grid lines for map effect
        val lineColor = Color(0x32C8C896)
        for (i in 1..4) {
            val offset = w * i / 5
            drawLine(lineColor, Offset(offset, 0f), Offset(offset, h), strokeWidth = 1f)
            drawLine(lineColor, Offset(0f, offset), Offset(w, offset), strokeWidth = 1f)
        }

        // Terrain shapes
        val terrainColor = Color(0x1E64B450)
        drawOval(
            color = terrainColor,
            topLeft = Offset(w * 0.1f, h * 0.6f),
            size = Size(w * 0.4f, h * 0.25f)
        )
        drawOval(
            color = terrainColor,
            topLeft = Offset(w * 0.55f, h * 0.15f),
            size = Size(w * 0.35f, h * 0.25f)
        )

        // Pin shadow
        drawOval(
            color = Color(0x3C000000),
            topLeft = Offset(w / 2 - w * 0.1f, h * 0.62f),
            size = Size(w * 0.2f, h * 0.06f)
        )

        // Pin location
        val pinX = w / 2
        val pinY = h * 0.38f
        val pinRadius = w * 0.13f

        // Pin head (red circle)
        drawCircle(
            color = Color(0xFFEA4335),
            radius = pinRadius,
            center = Offset(pinX, pinY)
        )

        // Pin point (triangle)
        val path = Path().apply {
            moveTo(pinX - pinRadius * 0.65f, pinY + pinRadius * 0.35f)
            lineTo(pinX, pinY + pinRadius * 2.3f)
            lineTo(pinX + pinRadius * 0.65f, pinY + pinRadius * 0.35f)
            close()
        }
        drawPath(path, Color(0xFFEA4335))

        // White dot center
        drawCircle(
            color = Color.White,
            radius = pinRadius * 0.38f,
            center = Offset(pinX, pinY)
        )

        // Subtle border
        drawRoundRect(
            color = Color(0x50FFFFFF),
            cornerRadius = CornerRadius(6f, 6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
    }
}
