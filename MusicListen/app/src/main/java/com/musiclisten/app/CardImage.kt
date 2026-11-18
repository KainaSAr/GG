package com.musiclisten.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

object CardImage {
    fun jpegBytes(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalStateException("Не удалось открыть фото")
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (longest / sample > 1600) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Не удалось прочитать фото")
        val rotated = rotate(resolver, uri, decoded)
        return try {
            val out = ByteArrayOutputStream()
            var quality = 82
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
            while (out.size() > 1_400_000 && quality > 50) {
                out.reset()
                quality -= 10
                rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            out.toByteArray()
        } finally {
            if (rotated !== decoded) decoded.recycle()
            rotated.recycle()
        }
    }

    private fun rotate(
        resolver: android.content.ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val degrees = resolver.openInputStream(uri)?.use { input ->
            when (
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
