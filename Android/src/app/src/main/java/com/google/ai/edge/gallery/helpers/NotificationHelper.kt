package com.google.ai.edge.gallery.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.R

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Translator",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createForegroundNotification(channelId: String): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Screen Translator")
            .setContentText("Translating screen content")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    fun getNotificationId(): Int = 1001 // Example ID
}