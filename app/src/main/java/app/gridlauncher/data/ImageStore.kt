package app.gridlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos for photo widgets. A picked photo is copied into the app's own files, scaled down and
 * turned upright, so it keeps working after the gallery forgets the permission or the original
 * is deleted.
 */
object ImageStore {
    private const val TAG = "ImageStore"
    private const val DIR = "images"
    /** Longest side of a stored photo, in pixels: sharp on a phone, small in memory. */
    private const val MAX_SIDE = 1440

    fun file(context: Context, path: String): File = File(context.filesDir, path)

    /** Copies [uri] in for widget [uid]. Returns the path to store, or null if it can't be read. */
    suspend fun import(context: Context, uri: Uri, uid: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            val upright = if (scale < 1f || rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }
            // A new name each time, so a changed photo never shows the old one from a cache.
            val path = "$DIR/$uid-${System.currentTimeMillis()}.jpg"
            val out = file(context, path)
            out.parentFile?.mkdirs()
            out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            upright.recycle()
            path
        }.onFailure { Log.w(TAG, "Couldn't import a photo", it) }.getOrNull()
    }

    /** Deletes stored photos that no widget uses any more. */
    suspend fun deleteUnused(context: Context, used: Set<String>) = withContext(Dispatchers.IO) {
        File(context.filesDir, DIR).listFiles()?.forEach { f ->
            if ("$DIR/${f.name}" !in used) f.delete()
        }
    }
}
