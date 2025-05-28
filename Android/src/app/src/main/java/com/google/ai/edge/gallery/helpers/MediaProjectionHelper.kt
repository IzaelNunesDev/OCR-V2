package com.google.ai.edge.gallery.helpers

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

class MediaProjectionHelper(private val context: Context) {
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun getMediaProjection(resultCode: Int, data: Intent): MediaProjection? {
        return mediaProjectionManager.getMediaProjection(resultCode, data)
    }
}