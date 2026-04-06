package com.ytsubexchange

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.ytsubexchange.network.SocketManager
import org.json.JSONObject

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val roomId = intent.getStringExtra("roomId") ?: return
        val callerId = intent.getStringExtra("callerId") ?: return
        
        when (action) {
            "DECLINE_CALL" -> {
                // Decline call via socket
                val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val token = prefs.getString("token", "") ?: ""
                SocketManager.endCall(roomId, token)
                
                // Clear notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(2000)
                
                // Store declined call info
                storeMissedCall(context, "declined", roomId, callerId)
            }
            "ACCEPT_CALL" -> {
                // Open app to accept call
                val acceptIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("acceptCall", true)
                    putExtra("roomId", roomId)
                    putExtra("callerId", callerId)
                }
                context.startActivity(acceptIntent)
                
                // Clear notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(2000)
            }
        }
    }
    
    private fun storeMissedCall(context: Context, type: String, roomId: String, callerId: String) {
        val prefs = context.getSharedPreferences("missed_calls", Context.MODE_PRIVATE)
        val missedCalls = prefs.getStringSet("calls", mutableSetOf()) ?: mutableSetOf()
        
        val callInfo = JSONObject().apply {
            put("type", type)
            put("roomId", roomId)
            put("callerId", callerId)
            put("timestamp", System.currentTimeMillis())
        }
        
        missedCalls.add(callInfo.toString())
        prefs.edit().putStringSet("calls", missedCalls).apply()
    }
}