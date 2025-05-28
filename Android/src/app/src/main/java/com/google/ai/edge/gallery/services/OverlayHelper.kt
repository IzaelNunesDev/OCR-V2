package com.google.ai.edge.gallery.services

import android.content.Context
import android.graphics.Rect
import com.google.ai.edge.gallery.utils.OverlayManager

class OverlayHelper(private val context: Context) {
    private val overlayManager = OverlayManager(context)

    fun updateOverlayText(blockId: String, boundingBox: Rect, translation: String) {
        overlayManager.updateOverlayText(blockId, boundingBox, translation)
    }

    fun removeAllOverlays() {
        overlayManager.removeAllOverlays()
    }
}