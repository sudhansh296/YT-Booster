package com.ytsubexchange.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SocketManager {
    private var socket: Socket? = null

    private val pendingEmits = mutableListOf<Pair<String, JSONObject>>()
    private val eventListeners = mutableMapOf<String, (Array<Any>) -> Unit>()

    private var userToken: String = ""
    private var isConnecting = false
    private var hasConnectedOnce = false
    private val joinedChatRooms = mutableSetOf<String>()

    // Trust all certs — same as RetrofitClient (self-signed SSL)
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // 0 = no timeout for long-polling
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    @Synchronized
    fun connect() {
        val s = socket
        if (s != null && (s.connected() || isConnecting)) return
        isConnecting = true

        Thread {
            try {
                val opts = IO.Options().apply {
                    reconnection = true
                    reconnectionAttempts = Int.MAX_VALUE
                    reconnectionDelay = 500          // 500ms (was 1000ms)
                    reconnectionDelayMax = 3000      // 3s max (was 5s)
                    timeout = 10000                  // 10s (was 20s)
                    transports = arrayOf("websocket") // websocket only — no polling fallback (faster)
                    callFactory = okHttpClient
                    webSocketFactory = okHttpClient
                }

                val newSocket = IO.socket(RetrofitClient.BASE_URL, opts)

                // Register all existing listeners
                eventListeners.forEach { (event, cb) ->
                    newSocket.on(event) { args -> cb(args) }
                }

                newSocket.on(Socket.EVENT_CONNECT) {
                    isConnecting = false
                    Log.d("Socket", "Connected: ${newSocket.id()}")

                    val isReconnect = hasConnectedOnce
                    hasConnectedOnce = true

                    // Flush pending emits (join_user_room is queued here on first connect)
                    synchronized(pendingEmits) {
                        val iter = pendingEmits.iterator()
                        while (iter.hasNext()) {
                            val (event, data) = iter.next()
                            newSocket.emit(event, data)
                            Log.d("Socket", "Flushed: $event")
                            iter.remove()
                        }
                    }

                    // On reconnect only — re-emit join_user_room (first connect uses pendingEmits flush)
                    if (isReconnect && userToken.isNotEmpty()) {
                        newSocket.emit("join_user_room", JSONObject().put("token", userToken))
                        Log.d("Socket", "join_user_room re-emitted on reconnect")
                    }

                    // Re-join previously joined chat rooms on reconnect
                    if (isReconnect) {
                        synchronized(joinedChatRooms) {
                            joinedChatRooms.forEach { roomId ->
                                newSocket.emit("join_chat_room", JSONObject().put("roomId", roomId).put("token", userToken))
                                Log.d("Socket", "Re-joined chat room: $roomId")
                            }
                        }
                    }
                }

                newSocket.on(Socket.EVENT_DISCONNECT) { args ->
                    Log.d("Socket", "Disconnected: ${args.getOrNull(0)}")
                }

                newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    isConnecting = false
                    Log.e("Socket", "Connect error: ${args.getOrNull(0)}")
                }

                socket = newSocket
                newSocket.connect()

            } catch (e: Exception) {
                isConnecting = false
                Log.e("Socket", "Exception: ${e.message}")
            }
        }.start()
    }

    fun joinUserRoom(token: String) {
        userToken = token
        // Remove any existing pending join_user_room to avoid duplicates
        synchronized(pendingEmits) {
            pendingEmits.removeAll { it.first == "join_user_room" }
        }
        emit("join_user_room", JSONObject().put("token", token))
    }

    fun joinQueue(token: String) {
        emit("join_queue", JSONObject().put("token", token))
    }

    fun confirmSubscribe(token: String, matchId: String) {
        emit("confirm_subscribe", JSONObject().put("token", token).put("matchId", matchId))
    }

    fun leaveQueue() {
        socket?.emit("leave_queue")
    }

    fun joinChatRoom(roomId: String, token: String = "") {
        synchronized(joinedChatRooms) {
            if (joinedChatRooms.contains(roomId)) {
                Log.d("Socket", "joinChatRoom: already joined $roomId, skipping")
                return
            }
            joinedChatRooms.add(roomId)
        }
        val data = JSONObject().put("roomId", roomId)
        if (token.isNotEmpty()) data.put("token", token)
        emit("join_chat_room", data)
    }

    fun leaveChatRoom(roomId: String) {
        synchronized(joinedChatRooms) { joinedChatRooms.remove(roomId) }
        socket?.emit("leave_chat_room", JSONObject().put("roomId", roomId))
    }

    fun leaveAllChatRooms() {
        val rooms: List<String>
        synchronized(joinedChatRooms) {
            rooms = joinedChatRooms.toList()
            joinedChatRooms.clear()
        }
        rooms.forEach { roomId ->
            socket?.emit("leave_chat_room", JSONObject().put("roomId", roomId))
            Log.d("Socket", "Left chat room: $roomId")
        }
    }

    fun sendChatMessage(roomId: String, text: String, replyTo: String? = null, token: String = "") {
        val data = JSONObject().put("roomId", roomId).put("text", text)
        if (replyTo != null) data.put("replyTo", replyTo)
        if (token.isNotEmpty()) data.put("token", token)
        emit("chat_message", data)
    }

    fun sendTyping(roomId: String, typing: Boolean) {
        emit("chat_typing", JSONObject().put("roomId", roomId).put("typing", typing))
    }

    // ── Call Signaling ────────────────────────────────────────
    fun startCall(roomId: String, callType: String, token: String = "") {
        val data = JSONObject().put("roomId", roomId).put("callType", callType)
        if (token.isNotEmpty()) data.put("token", token)
        emit("call_start", data)
    }

    fun joinCall(roomId: String, callerId: String, token: String = "") {
        val data = JSONObject().put("roomId", roomId).put("callerId", callerId)
        if (token.isNotEmpty()) data.put("token", token)
        emit("call_join", data)
    }

    fun endCall(roomId: String, token: String = "") {
        val data = JSONObject().put("roomId", roomId)
        if (token.isNotEmpty()) data.put("token", token)
        emit("call_end", data)
    }

    fun sendOffer(targetSocketId: String, offer: String, roomId: String) {
        emit("webrtc_offer", JSONObject().put("targetSocketId", targetSocketId).put("offer", offer).put("roomId", roomId))
    }

    fun sendAnswer(targetSocketId: String, answer: String) {
        emit("webrtc_answer", JSONObject().put("targetSocketId", targetSocketId).put("answer", answer))
    }

    fun sendIceCandidate(targetSocketId: String, candidate: String) {
        emit("webrtc_ice", JSONObject().put("targetSocketId", targetSocketId).put("candidate", candidate))
    }

    fun upgradeCall(roomId: String, newType: String, token: String = "") {
        val data = JSONObject().put("roomId", roomId).put("newType", newType)
        if (token.isNotEmpty()) data.put("token", token)
        emit("call_upgrade", data)
    }

    // ── Group Voice Chat ──────────────────────────────────────
    fun joinVoiceChat(roomId: String, token: String = "") {
        val data = JSONObject().put("roomId", roomId)
        if (token.isNotEmpty()) data.put("token", token)
        emit("voice_chat_join", data)
    }

    fun leaveVoiceChat(roomId: String) {
        emit("voice_chat_leave", JSONObject().put("roomId", roomId))
    }

    fun sendVoiceChatMute(roomId: String, muted: Boolean) {
        emit("voice_chat_mute", JSONObject().put("roomId", roomId).put("muted", muted))
    }

    fun sendVoiceChatOffer(targetSocketId: String, offer: String, roomId: String) {
        emit("voice_chat_offer", JSONObject().put("targetSocketId", targetSocketId).put("offer", offer).put("roomId", roomId))
    }

    fun sendVoiceChatAnswer(targetSocketId: String, answer: String, roomId: String) {
        emit("voice_chat_answer", JSONObject().put("targetSocketId", targetSocketId).put("answer", answer).put("roomId", roomId))
    }

    fun sendVoiceChatIce(targetSocketId: String, candidate: String, roomId: String) {
        emit("voice_chat_ice", JSONObject().put("targetSocketId", targetSocketId).put("candidate", candidate).put("roomId", roomId))
    }

    fun getVoiceChatParticipants(roomId: String) {
        emit("voice_chat_get_participants", JSONObject().put("roomId", roomId))
    }

    fun adminMuteVoiceChat(roomId: String, targetUserId: String, muted: Boolean) {
        emit("voice_chat_admin_mute", JSONObject().put("roomId", roomId).put("targetUserId", targetUserId).put("muted", muted))
    }

    fun adminKickVoiceChat(roomId: String, targetUserId: String) {
        emit("voice_chat_admin_kick", JSONObject().put("roomId", roomId).put("targetUserId", targetUserId))
    }

    fun adminEndVoiceChat(roomId: String) {
        emit("voice_chat_admin_end", JSONObject().put("roomId", roomId))
    }

    fun getSocketId(): String? = socket?.id()

    fun emit(event: String, data: JSONObject = JSONObject()) {
        val s = socket
        if (s != null && s.connected()) {
            s.emit(event, data)
            Log.d("Socket", "emit: $event")
        } else {
            synchronized(pendingEmits) {
                pendingEmits.add(Pair(event, data))
            }
            Log.d("Socket", "queued: $event")
            connect()
        }
    }

    fun on(event: String, callback: (Array<Any>) -> Unit) {
        socket?.off(event)
        eventListeners[event] = callback
        socket?.on(event) { args -> callback(args) }
    }

    fun off(event: String) {
        socket?.off(event)
        eventListeners.remove(event)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        pendingEmits.clear()
        isConnecting = false
        hasConnectedOnce = false
        synchronized(joinedChatRooms) { joinedChatRooms.clear() }
    }

    fun reconnect() {
        if (socket?.connected() == true) return
        socket?.disconnect()
        socket = null
        isConnecting = false
        connect()
        Log.d("Socket", "reconnect() called")
    }
}
