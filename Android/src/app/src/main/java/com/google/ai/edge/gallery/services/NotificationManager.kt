package com.google.ai.edge.gallery.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R

class NotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "ScreenTranslatorChannel"
        private const val CHANNEL_NAME = "Screen Translator Service Channel"
        private const val CHANNEL_DESCRIPTION = "Channel for Screen Translator foreground service notifications"
        private const val NOTIFICATION_ID = 1001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createForegroundNotification(): android.app.Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screen Translator Active")
            .setContentText("Tap to configure or stop the service.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a suitable icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissable
            .build()
    }

    fun getNotificationId(): Int {
        return NOTIFICATION_ID
    }
}