package com.google.ai.edge.gallery.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R

class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Translator",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Canal para notificações do serviço de tradução de tela"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createForegroundNotification(channelId: String): Notification {
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

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Screen Translator Ativo")
            .setContentText("Toque para configurar ou parar o serviço")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    companion object {
        const val ONGOING_NOTIFICATION_ID = 1001
    }
}