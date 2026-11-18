package com.musiclisten.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

object CardImage {
    fun sampleSize(width: Int, height: Int, maxEdge: Int = 1600): Int {
        var sample = 1
        val longest = max(width, height)
        if (longest <= 0) return 1
        while (longest / sample > maxEdge) sample *= 2
        return sample
    }

    fun jpegBytes(context: Context, uri: Uri): ByteArray {
        // Copy first. decodeStream returns null when only bounds are requested,
        // and many gallery streams cannot be rewound.
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Не удалось открыть фото")
        if (bytes.isEmpty()) throw IllegalStateException("Фото пустое")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Это не изображение")
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) },
        ) ?: throw IllegalStateException("Не удалось прочитать фото")
        val rotated = rotate(bytes, decoded)
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

    private fun rotate(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
