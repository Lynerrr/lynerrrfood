package com.lynerrr.food.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Turns a camera or gallery photo into an upright, downscaled JPEG suitable for sending to Claude. */
object MealPhoto {
    /** Long side in pixels: enough detail for food, and keeps the request small (≈1.5k image tokens). */
    private const val MAX_SIDE = 1568

    fun load(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Can't read this image")

        val rotation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).rotationDegrees } ?: 0
        }
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        if (rotation == 0 && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    fun toJpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
        it.toByteArray()
    }
}
