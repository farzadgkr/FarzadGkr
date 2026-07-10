package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ImageStorageHelper {

    fun saveImageToGallery(context: Context, bitmap: Bitmap, filename: String): String? {
        val resolver = context.contentResolver
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HeyCyanG300")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var imageUri: Uri? = null
        var outputStream: OutputStream? = null

        return try {
            imageUri = resolver.insert(imageCollection, contentValues)
            if (imageUri != null) {
                outputStream = resolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }
                    imageUri.toString()
                } else {
                    null
                }
            } else {
                // Fallback for older versions if MediaStore fails
                saveImageLegacy(context, bitmap, filename)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Clean up if failed
            imageUri?.let { uri ->
                try {
                    resolver.delete(uri, null, null)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            null
        } finally {
            outputStream?.close()
        }
    }

    private fun saveImageLegacy(context: Context, bitmap: Bitmap, filename: String): String? {
        return try {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "HeyCyanG300")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, "$filename.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Trigger Media Scanner to make it show up in gallery
            val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(file)
            context.sendBroadcast(mediaScanIntent)
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
