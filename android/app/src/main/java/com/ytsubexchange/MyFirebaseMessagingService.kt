package com.ytsubexchange

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val type = remoteMessage.data["type"] ?: ""
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "YT-Booster"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""

        when (type) {
            "incoming_call" -> {
                val callType = remoteMessage.data["callType"] ?: "voice"
                val roomId = remoteMessage.data["roomId"] ?: ""
                val callerId = remoteMessage.data["callerId"] ?: ""
                val roomName = remoteMessage.data["roomName"] ?: ""
                showCallNotification(title, body, remoteMessage.data)
                storeMissedCall(callType, roomId, callerId, roomName, title)
            }
            "chat_message" -> {
                val roomId = remoteMessage.data["roomId"] ?: ""
                val senderId = remoteMessage.data["senderId"] ?: ""
                val messageId = remoteMessage.data["messageId"] ?: ""
                val timestamp = remoteMessage.data["timestamp"] ?: ""
                storeOfflineMessage(roomId, senderId, title, body, messageId, timestamp)
                showNotification(title, body, "chat", roomId)
            }
            "chat_request" -> {
                val fromUserId = remoteMessage.data["fromUserId"] ?: ""
                showNotification(title, body, "chat_request", fromUserId)
            }
            "group_invite" -> {
                val roomId = remoteMessage.data["roomId"] ?: ""
                showNotification(title, body, "group_invite", roomId)
            }
            else -> showNotification(title, body)
        }
    }

    private fun storeMissedCall(callType: String, roomId: String, callerId: String, roomName: String, callerName: String) {
        val prefs = getSharedPreferences("missed_calls", Context.MODE_PRIVATE)
        val missedCalls = prefs.getStringSet("calls", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val callInfo = org.json.JSONObject().apply {
            put("callType", callType)
            put("roomId", roomId)
            put("callerId", callerId)
            put("roomName", roomName)
            put("callerName", callerName)
            put("timestamp", System.currentTimeMillis())
        }
        missedCalls.add(callInfo.toString())
        prefs.edit().putStringSet("calls", missedCalls).apply()
    }

    private fun storeOfflineMessage(roomId: String, senderId: String, senderName: String, messageText: String, messageId: String, timestamp: String) {
        val prefs = getSharedPreferences("offline_messages", Context.MODE_PRIVATE)
        val messages = prefs.getStringSet("messages", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val msgInfo = org.json.JSONObject().apply {
            put("roomId", roomId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("text", messageText)
            put("messageId", messageId)
            put("timestamp", timestamp)
            put("receivedAt", System.currentTimeMillis())
        }
        messages.add(msgInfo.toString())
        prefs.edit().putStringSet("messages", messages).apply()
    }

    override fun onNewToken(token: String) {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        val authToken = prefs.getString("token", null) ?: return
        Thread {
            try {
                com.ytsubexchange.network.RetrofitClient.api
                    .updateFcmToken("Bearer $authToken", mapOf("fcmToken" to token))
                    .execute()
            } catch (e: Exception) { /* silent */ }
        }.start()
    }

    private fun showNotification(title: String, body: String, type: String = "default", extraData: String = "") {
        val channelId = "ytbooster_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "YT-Booster", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showCallNotification(title: String, body: String, data: Map<String, String>) {
        val channelId = "call_notifications"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_incoming", true)
            putExtra("call_type", data["callType"] ?: "voice")
            putExtra("room_id", data["roomId"] ?: "")
            putExtra("room_name", data["roomName"] ?: "")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1, notification)
    }
}
