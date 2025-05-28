package com.google.ai.edge.gallery.services

import android.graphics.Bitmap
import android.media.Image
import android.util.Log

class ImageProcessingHelper {
    
    companion object {
        private const val TAG = "ImageProcessingHelper"
    }

    fun processImageToBitmap(image: Image, screenWidth: Int, screenHeight: Int): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmapWidth = screenWidth + rowPadding / pixelStride
            val tempBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
            
            try {
                tempBitmap.copyPixelsFromBuffer(buffer)
                
                if (rowPadding > 0) {
                    val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, screenWidth, screenHeight)
                    tempBitmap.recycle() // Recicla o bitmap temporário
                    finalBitmap
                } else {
                    tempBitmap
                }
            } catch (e: Exception) {
                tempBitmap.recycle() // Garante reciclagem em caso de erro
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar imagem para bitmap", e)
            null
        }
    }

    fun logImageProcessing(image: Image) {
        Log.d(TAG, "Processando imagem com timestamp: ${image.timestamp}")
    }
}