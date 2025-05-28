package com.google.ai.edge.gallery.helpers

import android.graphics.Bitmap
import android.media.Image

class ImageProcessingHelper {
    fun processImageToBitmap(image: Image, screenWidth: Int, screenHeight: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmapWidth = screenWidth + rowPadding / pixelStride
        val tempBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
        tempBitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            Bitmap.createBitmap(tempBitmap, 0, 0, screenWidth, screenHeight).also {
                tempBitmap.recycle()
            }
        } else {
            tempBitmap
        }
    }
}