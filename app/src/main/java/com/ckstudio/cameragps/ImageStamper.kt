package com.ckstudio.cameragps

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*
import androidx.core.graphics.withClip

/**
 * Stamps a GPS location banner onto a photo bitmap, matching the reference design:
 * - Semi-transparent dark background at the bottom
 * - Map thumbnail placeholder on the left with location pin
 * - Location name, address, coordinates, date/time on the right
 * - "GPS Map Camera" branding in the top-right of the banner
 */
object ImageStamper {

    fun stampBanner(
        originalBitmap: Bitmap,
        locationData: LocationData,
        countryFlag: String,
        mapTileBitmap: Bitmap? = null,
        leftScale: Float = 1f,
        rightScale: Float = 1f,
        bottomScale: Float = 1f,
        gapScale: Float = 1f,
        heightScale: Float = 1f
    ): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height

        val result = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // --- Configure your margins here (as a percentage of image width) ---
        // For example, 0.045f means 4.5% of the total image width.
        val leftMargin = (width * 0.045f) * leftScale
        val rightMargin = (width * 0.045f) * rightScale
        val bottomMargin = (width * 0.06f) * bottomScale
        
        // Banner dimensions — ~31% of image height for more text room
        val bannerHeight = (height * 0.31f * heightScale).toInt()
        val gap = (width * 0.03f) * gapScale // Gap between map and text banner

        // Map thumbnail (left side)
        val thumbSize = (bannerHeight * 0.7f).toInt() // Stable size independent of margins
        val thumbTop = height - bottomMargin - thumbSize
        val thumbLeft = leftMargin

        if (mapTileBitmap != null) {
            val mapRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)
            val srcRect = Rect(0, 0, mapTileBitmap.width, mapTileBitmap.height)
            
            // Clip map image to rounded rectangle
            val cornerRadius = thumbSize * 0.08f // Dynamic radius instead of hardcoded 16f
            val clipPath = Path().apply {
                addRoundRect(mapRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.withClip(clipPath) {
                // Draw real map tile
                drawBitmap(mapTileBitmap, srcRect, mapRect, Paint(Paint.ANTI_ALIAS_FLAG))

                // Draw "Google" text centered at bottom with black stroke
                val googlePaintStroke = Paint().apply {
                    color = Color.BLACK
                    textSize = thumbSize * 0.18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = thumbSize * 0.02f
                }
                val googlePaintFill = Paint().apply {
                    color = Color.WHITE
                    textSize = thumbSize * 0.18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
                val textWidth = googlePaintFill.measureText("Google")
                val textX = thumbLeft + (thumbSize - textWidth) / 2f
                val textY = thumbTop + thumbSize - thumbSize * 0.08f // Bottom-center

                drawText("Google", textX, textY, googlePaintStroke)
                drawText("Google", textX, textY, googlePaintFill)

                // Draw red pin on top of real map
                drawPinOnMap(this, thumbLeft, thumbTop, thumbSize.toFloat())

            }

            // Border around the clipped map
            val borderPaint = Paint().apply {
                color = Color.argb(80, 255, 255, 255)
                strokeWidth = thumbSize * 0.01f // dynamic stroke width
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            canvas.drawRoundRect(mapRect, cornerRadius, cornerRadius, borderPaint)
        } else {
            drawMapThumbnail(canvas, leftMargin, thumbTop, thumbSize.toFloat())
        }

        // Text area background
        val textLeft = thumbLeft + thumbSize + gap
        val textRight = width - rightMargin
        
        val bgPaint = Paint().apply {
            color = Color.argb(165, 0, 0, 0) // ~65% opacity
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textBgRect = RectF(textLeft, thumbTop, textRight, thumbTop + thumbSize)
        val textCornerRadius = thumbSize * 0.08f // Dynamic radius
        canvas.drawRoundRect(textBgRect, textCornerRadius, textCornerRadius, bgPaint)
        
        // Fill the top-right corner to make it square so the tab connects seamlessly
        val topRightSquareRect = RectF(
            textBgRect.right - textCornerRadius,
            textBgRect.top,
            textBgRect.right,
            textBgRect.top + textCornerRadius
        )
        canvas.drawRect(topRightSquareRect, bgPaint)

        // Text padding inside the rounded rectangle
        val textInnerPadding = thumbSize * 0.1f // Stable inner padding
        val textStartX = textLeft + textInnerPadding

        // Scale text sizes relative to thumbnail (which scales with banner)
        val brandingSize = thumbSize * 0.10f
        val nameSize = thumbSize * 0.17f
        val detailSize = thumbSize * 0.13f

        // --- Setup Paints ---
        val brandingPaint = android.text.TextPaint().apply {
            color = Color.WHITE
            textSize = brandingSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val namePaint = android.text.TextPaint().apply {
            color = Color.WHITE
            textSize = nameSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val detailPaint = android.text.TextPaint().apply {
            color = Color.WHITE
            textSize = detailSize
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        // Max width for text wrapping/ellipsizing
        val maxTextWidth = (textRight - textStartX - textInnerPadding)

        val nameText = "${locationData.locationName} $countryFlag"
        val coordText = "Lat ${locationData.latString} Long ${locationData.longString}"
        val brandingText = "\uD83D\uDDFA GPS Map Camera"

        // Dynamically scale down font sizes if they exceed max width
        var scale = 1f
        while (scale > 0.4f) {
            val maxCurrentWidth = maxOf(
                brandingPaint.measureText(brandingText),
                namePaint.measureText(nameText),
                detailPaint.measureText(locationData.fullAddress),
                detailPaint.measureText(coordText),
                detailPaint.measureText(locationData.formattedDateTime)
            )
            if (maxCurrentWidth > maxTextWidth) {
                scale -= 0.05f
                brandingPaint.textSize = brandingSize * scale
                namePaint.textSize = nameSize * scale
                detailPaint.textSize = detailSize * scale
            } else {
                break
            }
        }

        // Calculate total text block height using final scaled sizes (excluding branding which is outside)
        val lineSpacing = thumbSize * 0.06f // Stable line spacing
        val totalTextHeight = namePaint.textSize + (detailPaint.textSize * 3) + (lineSpacing * 3)
        
        // 1. Branding (top-right, drawn ABOVE the background box)
        val brandingWidth = brandingPaint.measureText(brandingText)
        val brandingPaddingY = thumbSize * 0.04f
        val brandingPaddingX = thumbSize * 0.08f
        val brandingHeight = brandingPaint.textSize + (brandingPaddingY * 2)
        
        // Draw the branding background tab
        val brandingBgRect = RectF(
            textRight - brandingWidth - (brandingPaddingX * 2),
            thumbTop - brandingHeight,
            textRight,
            thumbTop
        )
        val brandingCornerRadius = thumbSize * 0.06f
        canvas.drawRoundRect(brandingBgRect, brandingCornerRadius, brandingCornerRadius, bgPaint)
        
        // Fill the bottom half of the branding tab to make it flat where it touches the main banner
        val brandingBottomSquare = RectF(
            brandingBgRect.left,
            brandingBgRect.bottom - brandingCornerRadius,
            brandingBgRect.right,
            brandingBgRect.bottom
        )
        canvas.drawRect(brandingBottomSquare, bgPaint)

        // Draw branding text inside its tab
        val brandingY = brandingBgRect.bottom - brandingPaddingY
        canvas.drawText(
            brandingText,
            brandingBgRect.right - brandingPaddingX - brandingWidth,
            brandingY,
            brandingPaint
        )

        // Starting Y position to perfectly center the remaining text vertically in the background
        var currentY = thumbTop + (thumbSize.toFloat() - totalTextHeight) / 2f + namePaint.textSize

        // 2. Location name
        canvas.drawText(nameText, textStartX, currentY, namePaint)
        
        currentY += detailPaint.textSize + lineSpacing

        // 3. Address
        canvas.drawText(locationData.fullAddress, textStartX, currentY, detailPaint)
        
        currentY += detailPaint.textSize + lineSpacing

        // 4. Coordinates
        canvas.drawText(coordText, textStartX, currentY, detailPaint)
        
        currentY += detailPaint.textSize + lineSpacing

        // 5. Date/Time
        canvas.drawText(locationData.formattedDateTime, textStartX, currentY, detailPaint)

        return result
    }

    /**
     * Draws just the red location pin (for overlay on a real map tile).
     */
    private fun drawPinOnMap(canvas: Canvas, left: Float, top: Float, size: Float) {
        val pinX = left + size / 2
        val pinY = top + size * 0.38f
        val pinRadius = size * 0.10f

        val shadowPaint = Paint().apply {
            color = Color.argb(50, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawOval(
            RectF(pinX - pinRadius, pinY + pinRadius * 2.3f, pinX + pinRadius, pinY + pinRadius * 2.7f),
            shadowPaint
        )

        val pinPaint = Paint().apply {
            color = Color.rgb(234, 67, 53)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(pinX, pinY, pinRadius, pinPaint)

        val pinPath = Path().apply {
            moveTo(pinX - pinRadius * 0.65f, pinY + pinRadius * 0.35f)
            lineTo(pinX, pinY + pinRadius * 2.3f)
            lineTo(pinX + pinRadius * 0.65f, pinY + pinRadius * 0.35f)
            close()
        }
        canvas.drawPath(pinPath, pinPaint)

        val dotPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(pinX, pinY, pinRadius * 0.38f, dotPaint)
    }

    /**
     * Downloads a satellite imagery tile from Esri World Imagery for the given coordinates.
     * This provides real aerial/satellite imagery similar to Google Maps satellite view.
     * Returns null if download fails.
     */
    fun downloadMapTile(latitude: Double, longitude: Double): Bitmap? {
        return try {
            val zoom = 16
            val n = (1 shl zoom).toDouble()
            val xTile = ((longitude + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(latitude)
            val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()

            // Esri World Imagery — free satellite tiles, no API key required
            val url = URL("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$yTile/$xTile")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "GPSCameraApp/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.connect()

            if (conn.responseCode == 200) {
                val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                bitmap
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Draws a satellite-style map thumbnail placeholder with a red location pin.
     */
    private fun drawMapThumbnail(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float
    ) {
        val mapRect = RectF(left, top, left + size, top + size)

        // Green gradient to simulate satellite view
        val shader = LinearGradient(
            left, top, left + size, top + size,
            intArrayOf(
                Color.rgb(34, 85, 34),
                Color.rgb(50, 120, 50),
                Color.rgb(30, 75, 40),
                Color.rgb(45, 100, 55)
            ),
            floatArrayOf(0f, 0.3f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        val mapBgPaint = Paint().apply {
            this.shader = shader
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(mapRect, 8f, 8f, mapBgPaint)

        // Grid lines for map effect
        val linePaint = Paint().apply {
            color = Color.argb(50, 200, 200, 150)
            strokeWidth = size * 0.008f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        for (i in 1..4) {
            val offset = size * i / 5
            canvas.drawLine(left + offset, top, left + offset, top + size, linePaint)
            canvas.drawLine(left, top + offset, left + size, top + offset, linePaint)
        }

        // Some organic shapes to simulate fields/terrain
        val terrainPaint = Paint().apply {
            color = Color.argb(30, 100, 180, 80)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawOval(
            RectF(left + size * 0.1f, top + size * 0.6f, left + size * 0.5f, top + size * 0.85f),
            terrainPaint
        )
        canvas.drawOval(
            RectF(left + size * 0.55f, top + size * 0.15f, left + size * 0.9f, top + size * 0.4f),
            terrainPaint
        )

        // Location pin
        val pinX = left + size / 2
        val pinY = top + size * 0.38f
        val pinRadius = size * 0.13f

        // Pin shadow
        val shadowPaint = Paint().apply {
            color = Color.argb(60, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawOval(
            RectF(pinX - pinRadius, pinY + pinRadius * 2.3f, pinX + pinRadius, pinY + pinRadius * 2.7f),
            shadowPaint
        )

        // Pin body (Google Maps red)
        val pinPaint = Paint().apply {
            color = Color.rgb(234, 67, 53)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(pinX, pinY, pinRadius, pinPaint)

        // Pin point (triangle)
        val pinPath = Path().apply {
            moveTo(pinX - pinRadius * 0.65f, pinY + pinRadius * 0.35f)
            lineTo(pinX, pinY + pinRadius * 2.3f)
            lineTo(pinX + pinRadius * 0.65f, pinY + pinRadius * 0.35f)
            close()
        }
        canvas.drawPath(pinPath, pinPaint)

        // White dot in center
        val dotPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(pinX, pinY, pinRadius * 0.38f, dotPaint)

        // Subtle border
        val borderPaint = Paint().apply {
            color = Color.argb(80, 255, 255, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(mapRect, 8f, 8f, borderPaint)
    }

    /**
     * Saves a stamped bitmap to the device gallery.
     * Uses MediaStore for API 29+ and legacy file I/O for older versions.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/GPS Camera"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, contentValues, null, null)
                    true
                } ?: false
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "GPS Camera"
                )
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // Notify media scanner
                val values = ContentValues().apply {
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
