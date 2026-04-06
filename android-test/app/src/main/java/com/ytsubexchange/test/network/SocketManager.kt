package com.ytsubexchange.test.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {
    private var socket: Socket? = null
    private val pendingEvents = mutableListOf<Pair<String, (Array<Any>) -> Unit>>()

    fun connect() {
        if (socket?.connected() == true) return
        // Background thread pe connect karo - main thread block na ho
        Thread {
            try {
                val opts = IO.Options().apply {
                    reconnection = true
                    reconnectionAttempts = 10
                    reconnectionDelay = 1000
                    timeout = 20000
                }
                socket = IO.socket(RetrofitClient.BASE_URL, opts)
                // Re-register pending listeners
                pendingEvents.forEach { (event, cb) ->
                    socket?.on(event) { args -> cb(args) }
                }
                socket?.on(Socket.EVENT_CONNECT) { Log.d("Socket", "Connected") }
                socket?.on(Socket.EVENT_DISCONNECT) { Log.d("Socket", "Disconnected") }
                socket?.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e("Socket", "Error: ${args[0]}") }
                socket?.connect()
            } catch (e: Exception) {
                Log.e("Socket", "Connect error: ${e.message}")
            }
        }.start()
    }

    fun joinQueue(token: String) {
        val data = JSONObject().put("token", token)
        Log.d("Socket", "joinQueue called, connected=${socket?.connected()}")
        if (socket?.connected() == true) {
            socket?.emit("join_queue", data)
            Log.d("Socket", "join_queue emitted")
        } else {
            socket?.once(Socket.EVENT_CONNECT) {
                socket?.emit("join_queue", data)
                Log.d("Socket", "join_queue emitted after connect")
            }
            if (socket == null) connect()
            else socket?.connect()
        }
    }

    fun confirmSubscribe(token: String, matchId: String) {
        val data = JSONObject().put("token", token).put("matchId", matchId)
        socket?.emit("confirm_subscribe", data)
    }

    fun leaveQueue() {
        socket?.emit("leave_queue")
    }

    fun on(event: String, callback: (Array<Any>) -> Unit) {
        pendingEvents.add(Pair(event, callback))
        socket?.on(event) { args -> callback(args) }
    }

    fun off(event: String) {
        socket?.off(event)
        pendingEvents.removeAll { it.first == event }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        pendingEvents.clear()
    }

    // ── Chat Socket Events ────────────────────────────────────
    fun joinChatRoom(roomId: String) {
        socket?.emit("join_chat_room", JSONObject().put("roomId", roomId))
    }

    fun leaveChatRoom(roomId: String) {
        socket?.emit("leave_chat_room", JSONObject().put("roomId", roomId))
    }

    fun sendChatMessage(roomId: String, text: String, replyToId: String? = null) {
        val data = JSONObject().put("roomId", roomId).put("text", text)
        if (replyToId != null) data.put("replyTo", replyToId)
        socket?.emit("chat_message", data)
    }

    fun sendTyping(roomId: String, typing: Boolean) {
        socket?.emit("chat_typing", JSONObject().put("roomId", roomId).put("typing", typing))
    }

    fun emit(event: String, data: JSONObject) {
        if (socket?.connected() == true) {
            socket?.emit(event, data)
        } else {
            socket?.once(Socket.EVENT_CONNECT) { socket?.emit(event, data) }
        }
    }
}
