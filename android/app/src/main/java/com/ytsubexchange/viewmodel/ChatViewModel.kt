package com.ytsubexchange.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytsubexchange.data.*
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _myIdFlow = MutableStateFlow(prefs.getString("userId", "") ?: "")
    val myIdFlow: StateFlow<String> = _myIdFlow
    val myId: String get() = _myIdFlow.value

    private fun fetchAndCacheMyId() {
        val stored = prefs.getString("userId", "") ?: ""
        if (stored.isNotEmpty()) {
            _myIdFlow.value = stored
            return
        }
        viewModelScope.launch {
            try {
                val profile = RetrofitClient.api.getProfile(token)
                if (profile._id.isNotEmpty()) {
                    prefs.edit().putString("userId", profile._id).apply()
                    _myIdFlow.value = profile._id
                }
            } catch (e: Exception) { }
        }
    }
    private val token: String get() = "Bearer ${prefs.getString("token", "")}"

    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val rooms: StateFlow<List<ChatRoom>> = _rooms

    private val _openRoom = MutableStateFlow<ChatRoom?>(null)
    val openRoom: StateFlow<ChatRoom?> = _openRoom

    // Per-room message cache — back karne pe delete nahi hoga
    // App band hone pe bhi persist hoga (SharedPreferences mein JSON)
    private val msgPrefs = app.getSharedPreferences("chat_messages", Context.MODE_PRIVATE)

    private fun saveMessagesToDisk(roomId: String, messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.takeLast(100).forEach { msg ->
                arr.put(JSONObject().apply {
                    put("_id", msg._id)
                    put("senderId", msg.senderId)
                    put("senderName", msg.senderName)
                    put("senderPic", msg.senderPic)
                    put("text", msg.text)
                    put("createdAt", msg.createdAt)
                    put("pinned", msg.pinned)
                    put("starred", msg.starred)
                    msg.fileUrl?.let { put("fileUrl", it) }
                    msg.fileType?.let { put("fileType", it) }
                    msg.fileName?.let { put("fileName", it) }
                    msg.replyTo?.let { r ->
                        put("replyTo", JSONObject().apply {
                            put("msgId", r.msgId)
                            put("text", r.text)
                            put("senderName", r.senderName)
                        })
                    }
                })
            }
            msgPrefs.edit().putString("room_$roomId", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun loadMessagesFromDisk(roomId: String): List<ChatMessage> {
        return try {
            val json = msgPrefs.getString("room_$roomId", null) ?: return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ChatMessage(
                    _id = obj.optString("_id"),
                    senderId = obj.optString("senderId"),
                    senderName = obj.optString("senderName"),
                    senderPic = obj.optString("senderPic"),
                    text = obj.optString("text"),
                    createdAt = obj.optString("createdAt"),
                    pinned = obj.optBoolean("pinned"),
                    starred = obj.optBoolean("starred"),
                    fileUrl = obj.optString("fileUrl").takeIf { it.isNotEmpty() },
                    fileType = obj.optString("fileType").takeIf { it.isNotEmpty() },
                    fileName = obj.optString("fileName").takeIf { it.isNotEmpty() },
                    replyTo = obj.optJSONObject("replyTo")?.let { r ->
                        ReplyRef(
                            msgId = r.optString("msgId"),
                            text = r.optString("text"),
                            senderName = r.optString("senderName")
                        )
                    }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private val _messageCache = mutableMapOf<String, List<ChatMessage>>()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _replyTo = MutableStateFlow<ReplyRef?>(null)
    val replyTo: StateFlow<ReplyRef?> = _replyTo

    private val _pinnedMsg = MutableStateFlow<ChatMessage?>(null)
    val pinnedMsg: StateFlow<ChatMessage?> = _pinnedMsg

    private val _contextMsg = MutableStateFlow<ChatMessage?>(null)
    val contextMsg: StateFlow<ChatMessage?> = _contextMsg

    private val _starredMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val starredMessages: StateFlow<List<ChatMessage>> = _starredMessages

    private val _showStarred = MutableStateFlow(false)
    val showStarred: StateFlow<Boolean> = _showStarred

    private val _showPinned = MutableStateFlow(false)
    val showPinned: StateFlow<Boolean> = _showPinned

    private val _users = MutableStateFlow<List<ChatUser>>(emptyList())
    val users: StateFlow<List<ChatUser>> = _users

    private val _showNewChat = MutableStateFlow(false)
    val showNewChat: StateFlow<Boolean> = _showNewChat

    private val _showCreateGroup = MutableStateFlow(false)
    val showCreateGroup: StateFlow<Boolean> = _showCreateGroup

    private val _toastMsg = MutableStateFlow<String?>(null)
    val toastMsg: StateFlow<String?> = _toastMsg

    data class InAppNotif(val senderName: String, val text: String, val roomId: String, val room: ChatRoom)
    private val _inAppNotif = MutableStateFlow<InAppNotif?>(null)
    val inAppNotif: StateFlow<InAppNotif?> = _inAppNotif
    fun clearInAppNotif() { _inAppNotif.value = null }

    // Group invite notification
    private val _groupInvite = MutableStateFlow<com.ytsubexchange.data.GroupInviteNotif?>(null)
    val groupInvite: StateFlow<com.ytsubexchange.data.GroupInviteNotif?> = _groupInvite
    fun clearGroupInvite() { _groupInvite.value = null }

    // Group info
    private val _groupInfo = MutableStateFlow<com.ytsubexchange.data.GroupInfoResponse?>(null)
    val groupInfo: StateFlow<com.ytsubexchange.data.GroupInfoResponse?> = _groupInfo
    private val _showGroupInfo = MutableStateFlow(false)
    val showGroupInfo: StateFlow<Boolean> = _showGroupInfo

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers

    fun isUserOnline(userId: String): Boolean = _onlineUsers.value.contains(userId)

    private val _communityMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val communityMessages: StateFlow<List<ChatMessage>> = _communityMessages
    private var inCommunity = false

    // Community status
    private val _communityOpen = MutableStateFlow(true)
    val communityOpen: StateFlow<Boolean> = _communityOpen
    private val _communityPinned = MutableStateFlow("")
    val communityPinned: StateFlow<String> = _communityPinned
    private val _communityOnline = MutableStateFlow(0)
    val communityOnline: StateFlow<Int> = _communityOnline

    // ── AI Companion ──────────────────────────────────────────
    private val _aiMessages = MutableStateFlow<List<AiMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiMessage>> = _aiMessages
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading

    fun sendAiMessage(text: String) {
        _aiMessages.value = _aiMessages.value + AiMessage("user", text)
        _aiLoading.value = true
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.aiChat(token, mapOf("message" to text))
                _aiMessages.value = _aiMessages.value + AiMessage("ai", resp.reply)
            } catch (e: Exception) {
                _aiMessages.value = _aiMessages.value + AiMessage("ai", "Sorry, kuch problem aayi. Dobara try karo.")
            } finally {
                _aiLoading.value = false
            }
        }
    }

    private val _pendingRequests = MutableStateFlow<List<com.ytsubexchange.data.ChatRequest>>(emptyList())
    val pendingRequests: StateFlow<List<com.ytsubexchange.data.ChatRequest>> = _pendingRequests

    private val _sentRequests = MutableStateFlow<List<com.ytsubexchange.data.SentRequest>>(emptyList())
    val sentRequests: StateFlow<List<com.ytsubexchange.data.SentRequest>> = _sentRequests

    private var typingJob: kotlinx.coroutines.Job? = null

    init {
        fetchAndCacheMyId()
        loadRooms()
        loadPendingRequests()
        loadSentRequests()
        loadBlockedUsers()
        listenSocketEvents()
        joinUserRoom()
    }

    fun loadRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = RetrofitClient.api.getChatRooms(token)
                _rooms.value = resp.rooms.sortedByDescending { it.lastTime ?: "" }
                // Rooms load hone ke baad DM partners ka online status fetch karo
                fetchOnlineStatus(resp.rooms)
            } catch (e: Exception) { } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchOnlineStatus(rooms: List<ChatRoom>) {
        val partnerIds = rooms
            .filter { !it.isGroup && it.otherUserId != null }
            .mapNotNull { it.otherUserId }
            .distinct()
        if (partnerIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getOnlineStatus(token, mapOf("userIds" to partnerIds))
                if (resp.onlineUsers.isNotEmpty()) {
                    _onlineUsers.value = _onlineUsers.value + resp.onlineUsers.toSet()
                }
            } catch (e: Exception) { }
        }
    }

    fun openRoom(room: ChatRoom) {
        val currentRoom = _openRoom.value
        if (currentRoom != null && currentRoom._id != room._id) {
            SocketManager.leaveChatRoom(currentRoom._id)
        }
        val alreadyOpen = currentRoom?._id == room._id
        _openRoom.value = room
        // Block state — server se latest state use karo (rooms list se)
        // Lekin agar already blocked hai toh override mat karo
        val latestRoom = _rooms.value.firstOrNull { it._id == room._id } ?: room
        val alreadyBlockedByMe = _isBlockedByMe.value && _openRoom.value?._id == room._id
        _isBlockedByMe.value = if (alreadyBlockedByMe) true else latestRoom.isBlockedByMe
        _isBlockedByThem.value = latestRoom.isBlockedByThem
        // Unread reset karo immediately jab room open ho
        _rooms.value = _rooms.value.map {
            if (it._id == room._id) it.copy(unread = 0) else it
        }
        // Disk cache se pehle dikhao — blank screen nahi aayega
        if (_messageCache[room._id] == null) {
            val diskMsgs = loadMessagesFromDisk(room._id)
            if (diskMsgs.isNotEmpty()) _messageCache[room._id] = diskMsgs
        }
        _messages.value = _messageCache[room._id] ?: emptyList()
        if (!alreadyOpen) _pinnedMsg.value = null
        SocketManager.joinChatRoom(room._id, prefs.getString("token", "") ?: "")
        loadMessages(room._id)
        // Mark messages as read when room opens
        markRoomRead(room._id)
    }

    fun closeRoom() {
        _openRoom.value?.let { SocketManager.leaveChatRoom(it._id) }
        _openRoom.value = null
        // Leave all joined chat rooms so server knows we're not in any room
        // This ensures chat_message_notify is sent correctly for future messages
        SocketManager.leaveAllChatRooms()
        // messages delete nahi karo — wapas aane pe history dikhegi
        _replyTo.value = null
        _pinnedMsg.value = null
        _showStarred.value = false
        _showPinned.value = false
        // Block state reset mat karo — rooms list se state preserve hogi
        // Naya room open hone pe openRoom() me fresh state set hogi
    }

    private fun loadMessages(roomId: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getChatMessages(token, roomId)
                _messages.value = resp.messages
                _messageCache[roomId] = resp.messages
                saveMessagesToDisk(roomId, resp.messages)
                _pinnedMsg.value = resp.messages.firstOrNull { it.pinned }
            } catch (e: Exception) { }
        }
    }

    fun sendMessage(roomId: String, text: String) {
        // Block check - agar blocked hai toh message mat bhejo
        if (_isBlockedByMe.value || _isBlockedByThem.value) {
            _toastMsg.value = "❌ Blocked user ko message nahi bhej sakte"
            return
        }
        val reply = _replyTo.value
        cancelReply()
        val tempMsg = ChatMessage(
            _id = "temp_${System.currentTimeMillis()}",
            senderId = myId, senderName = "Me", senderPic = "", text = text,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()).format(java.util.Date()),
            replyTo = reply
        )
        _messages.value = _messages.value + tempMsg
        _messageCache[roomId] = _messages.value
        saveMessagesToDisk(roomId, _messages.value)
        // Socket se bhejo — server broadcast karega (fast)
        SocketManager.sendChatMessage(roomId, text, reply?.msgId, prefs.getString("token", "") ?: "")
        onTyping(roomId, false)
    }

    fun sendFile(roomId: String, uri: android.net.Uri, mimeType: String, displayName: String) {
        val ctx = getApplication<Application>()

        // File size check — 100MB limit
        val fileSize = try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) { 0L }
        if (fileSize > 100 * 1024 * 1024) {
            _toastMsg.value = "❌ File size limit 100MB only. Ye file ${fileSize / (1024 * 1024)}MB hai."
            return
        }

        // Clean filename — Uri.lastPathSegment garbage fix
        val cleanName = run {
            // Try content resolver display name first
            var name = ""
            try {
                val cursor = ctx.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && it.moveToFirst()) name = it.getString(idx) ?: ""
                }
            } catch (e: Exception) { }
            if (name.isBlank()) {
                // Fallback: extract just filename from path
                val raw = displayName.substringAfterLast('/').substringAfterLast(':')
                if (raw.isNotBlank()) raw else "file_${System.currentTimeMillis()}"
            } else name
        }

        val tempText = when {
            mimeType.startsWith("image") -> "📷 Photo"
            mimeType.startsWith("video") -> "🎥 Video"
            mimeType.startsWith("audio") -> "🎤 Voice"
            else -> "📄 $cleanName"
        }
        val tempId = "temp_${System.currentTimeMillis()}"
        val tempMsg = ChatMessage(
            _id = tempId, senderId = myId, senderName = "Me", senderPic = "", text = tempText,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()).format(java.util.Date())
        )
        _messages.value = _messages.value + tempMsg
        _messageCache[roomId] = _messages.value

        viewModelScope.launch {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                if (bytes == null || bytes.isEmpty()) {
                    _toastMsg.value = "File read nahi ho saki"
                    _messages.value = _messages.value.filter { it._id != tempId }
                    return@launch
                }
                val safeMime = if (mimeType.isBlank() || mimeType == "*/*") "application/octet-stream" else mimeType
                val reqBody = bytes.toRequestBody(safeMime.toMediaTypeOrNull())
                val part = okhttp3.MultipartBody.Part.createFormData("file", cleanName, reqBody)
                val roomIdBody = roomId.toRequestBody("text/plain".toMediaTypeOrNull())
                val msg = RetrofitClient.api.uploadChatFile(token, part, roomIdBody)
                val updated = _messages.value.map { if (it._id == tempId) msg else it }
                _messages.value = updated
                _messageCache[roomId] = updated
                saveMessagesToDisk(roomId, updated)
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "sendFile error: ${e.message}")
                val errMsg = when {
                    e.message?.contains("413") == true -> "❌ File size limit 100MB only"
                    e.message?.contains("403") == true -> "❌ File send nahi ho saka (blocked)"
                    e.message?.contains("404") == true -> "❌ Upload route nahi mila"
                    else -> "❌ File send nahi ho saka: ${e.message?.take(50)}"
                }
                _toastMsg.value = errMsg
                _messages.value = _messages.value.filter { it._id != tempId }
            }
        }
    }

    fun sendVoiceFile(roomId: String, file: java.io.File) {
        val tempId = "temp_${System.currentTimeMillis()}"
        val tempMsg = ChatMessage(
            _id = tempId, senderId = myId, senderName = "Me", senderPic = "", text = "🎤 Voice",
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()).format(java.util.Date())
        )
        _messages.value = _messages.value + tempMsg
        _messageCache[roomId] = _messages.value

        viewModelScope.launch {
            try {
                android.util.Log.d("ChatVM", "sendVoiceFile: Starting upload for file: ${file.name}, size: ${file.length()} bytes")
                
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    android.util.Log.e("ChatVM", "sendVoiceFile: Voice file is empty")
                    _toastMsg.value = "Voice file empty"
                    _messages.value = _messages.value.filter { it._id != tempId }
                    return@launch
                }
                
                android.util.Log.d("ChatVM", "sendVoiceFile: File read successfully, ${bytes.size} bytes")
                
                val reqBody = bytes.toRequestBody("audio/m4a".toMediaTypeOrNull())
                val part = okhttp3.MultipartBody.Part.createFormData("file", file.name, reqBody)
                val roomIdBody = roomId.toRequestBody("text/plain".toMediaTypeOrNull())
                
                android.util.Log.d("ChatVM", "sendVoiceFile: Making API call to uploadChatFile")
                val msg = RetrofitClient.api.uploadChatFile(token, part, roomIdBody)
                
                android.util.Log.d("ChatVM", "sendVoiceFile: Upload successful, message ID: ${msg._id}")
                val updated = _messages.value.map { if (it._id == tempId) msg else it }
                _messages.value = updated
                _messageCache[roomId] = updated
                saveMessagesToDisk(roomId, updated)
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "sendVoiceFile error: ${e.message}", e)
                val errMsg = when {
                    e.message?.contains("413") == true -> "❌ File size limit 50MB only"
                    e.message?.contains("403") == true -> "❌ Voice send nahi ho saka (blocked)"
                    e.message?.contains("401") == true -> "❌ Authentication error - please login again"
                    e.message?.contains("timeout") == true -> "❌ Upload timeout - try again"
                    e.message?.contains("500") == true -> "❌ Server error - try again later"
                    else -> "❌ Voice send nahi ho saka: ${e.message?.take(50)}"
                }
                _toastMsg.value = errMsg
                _messages.value = _messages.value.filter { it._id != tempId }
            }
        }
    }

    fun onTyping(roomId: String, typing: Boolean) {
        typingJob?.cancel()
        SocketManager.sendTyping(roomId, typing)
        if (typing) {
            typingJob = viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                SocketManager.sendTyping(roomId, false)
            }
        }
    }

    fun onMsgLongPress(msg: ChatMessage) { _contextMsg.value = msg }
    fun dismissContext() { _contextMsg.value = null }

    fun setReplyFromContext() {
        _contextMsg.value?.let { _replyTo.value = ReplyRef(msgId = it._id, text = it.text, senderName = it.senderName) }
        dismissContext()
    }

    fun setReply(msg: ChatMessage) {
        _replyTo.value = ReplyRef(msgId = msg._id, text = msg.text, senderName = msg.senderName)
    }

    fun cancelReply() { _replyTo.value = null }

    fun reactToMessage(emoji: String) {
        val msg = _contextMsg.value ?: return
        dismissContext()
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.reactToMessage(token, ReactRequest(msg._id, emoji))
                _messages.value = _messages.value.map { if (it._id == msg._id) it.copy(reactions = resp.reactions) else it }
            } catch (e: Exception) { _toastMsg.value = "React failed" }
        }
    }

    fun starMessage() {
        val msg = _contextMsg.value ?: return
        val roomId = _openRoom.value?._id ?: return
        dismissContext()
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.starMessage(token, StarRequest(msg._id))
                val updated = _messages.value.map { if (it._id == msg._id) it.copy(starred = resp.starred) else it }
                _messages.value = updated
                _messageCache[roomId] = updated
                saveMessagesToDisk(roomId, updated)
                if (!resp.starred) {
                    _starredMessages.value = _starredMessages.value.filter { it._id != msg._id }
                }
                _toastMsg.value = if (resp.starred) "Message starred ⭐" else "Star hataya ✓"
            } catch (e: Exception) { _toastMsg.value = "Star failed" }
        }
    }

    fun starMessageById(msgId: String) {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.starMessage(token, StarRequest(msgId))
                val updated = _messages.value.map { if (it._id == msgId) it.copy(starred = resp.starred) else it }
                _messages.value = updated
                _messageCache[roomId] = updated
                saveMessagesToDisk(roomId, updated)
                if (!resp.starred) {
                    _starredMessages.value = _starredMessages.value.filter { it._id != msgId }
                }
                _toastMsg.value = if (resp.starred) "Message starred ⭐" else "Star hataya ✓"
            } catch (e: Exception) { _toastMsg.value = "Star failed" }
        }
    }

    fun pinMessage() {
        val msg = _contextMsg.value ?: return
        val roomId = _openRoom.value?._id ?: return
        dismissContext()
        val isAlreadyPinned = _pinnedMsg.value?._id == msg._id
        viewModelScope.launch {
            try {
                if (isAlreadyPinned) {
                    // Unpin
                    RetrofitClient.api.pinMessage(token, PinRequest("", roomId))
                    _pinnedMsg.value = null
                    _toastMsg.value = "Message unpin ho gaya"
                } else {
                    // Pin
                    RetrofitClient.api.pinMessage(token, PinRequest(msg._id, roomId))
                    _pinnedMsg.value = msg
                    _toastMsg.value = "Message pinned 📌"
                }
            } catch (e: Exception) {
                // Optimistic update
                if (isAlreadyPinned) _pinnedMsg.value = null else _pinnedMsg.value = msg
            }
        }
    }

    fun clearPin() { _pinnedMsg.value = null }

    fun deleteMessage() {
        val msg = _contextMsg.value ?: return
        dismissContext()
        viewModelScope.launch {
            try { RetrofitClient.api.deleteMessage(token, msg._id); _messages.value = _messages.value.filter { it._id != msg._id }; _toastMsg.value = "Message deleted" }
            catch (e: Exception) { _toastMsg.value = "Delete failed" }
        }
    }

    fun copyMessage(): String { val text = _contextMsg.value?.text ?: ""; dismissContext(); return text }

    fun showStarredPanel() {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try { val resp = RetrofitClient.api.getStarredMessages(token, roomId); _starredMessages.value = resp.messages; _showStarred.value = true }
            catch (e: Exception) { _toastMsg.value = "Could not load starred" }
        }
    }
    fun hideStarredPanel() { _showStarred.value = false }
    fun showPinnedPanel() { _showPinned.value = true }
    fun hidePinnedPanel() { _showPinned.value = false }

    fun showNewChatDialog() { loadUsers(""); _showNewChat.value = true }
    fun hideNewChatDialog() { _showNewChat.value = false }
    fun showCreateGroupDialog() { loadUsers(""); _showCreateGroup.value = true; _showNewChat.value = false }
    fun hideCreateGroupDialog() { _showCreateGroup.value = false }

    fun loadUsers(query: String) {
        viewModelScope.launch {
            try { val resp = RetrofitClient.api.getChatUsers(token, query); _users.value = resp.users } catch (e: Exception) { }
        }
    }

    fun sendChatRequest(user: ChatUser) {
        _showNewChat.value = false
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.sendChatRequest(token, com.ytsubexchange.data.SendChatRequestRequest(user._id))
                if (resp.alreadyConnected && resp.room != null) {
                    val room = resp.room.copy(name = user.channelName, pic = user.profilePic)
                    _rooms.value = listOf(room) + _rooms.value.filter { it._id != room._id }
                    openRoom(room)
                } else {
                    // Sent requests list mein add karo
                    val sent = com.ytsubexchange.data.SentRequest(
                        requestId = resp.requestId,
                        toUserId = user._id,
                        toName = user.channelName,
                        toPic = user.profilePic
                    )
                    _sentRequests.value = listOf(sent) + _sentRequests.value.filter { it.toUserId != user._id }
                    _toastMsg.value = "${user.channelName} ko request bhej di ✓"
                }
            } catch (e: Exception) { _toastMsg.value = "Request nahi bhej saka" }
        }
    }

    fun acceptChatRequest(requestId: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.acceptChatRequest(token, com.ytsubexchange.data.AcceptChatRequestRequest(requestId))
                _pendingRequests.value = _pendingRequests.value.filter { it.requestId != requestId }
                if (resp.room != null) {
                    _rooms.value = listOf(resp.room) + _rooms.value.filter { it._id != resp.room._id }
                    openRoom(resp.room)
                }
                loadRooms()
            } catch (e: Exception) { _toastMsg.value = "Accept nahi ho saka" }
        }
    }

    fun rejectChatRequest(requestId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.rejectChatRequest(token, com.ytsubexchange.data.RejectChatRequestRequest(requestId))
                _pendingRequests.value = _pendingRequests.value.filter { it.requestId != requestId }
                _toastMsg.value = "Request reject kar di"
            } catch (e: Exception) { _toastMsg.value = "Reject nahi ho saka" }
        }
    }

    fun loadPendingRequests() {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getPendingRequests(token)
                _pendingRequests.value = resp.requests
            } catch (e: Exception) { }
        }
    }

    fun loadSentRequests() {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getSentRequests(token)
                _sentRequests.value = resp.requests
            } catch (e: Exception) { }
        }
    }

    private fun joinUserRoom() {
        val rawToken = prefs.getString("token", "") ?: ""
        SocketManager.joinUserRoom(rawToken)
    }

    fun createGroup(name: String, memberIds: List<String>) {
        _showCreateGroup.value = false
        viewModelScope.launch {
            try { val resp = RetrofitClient.api.createGroup(token, CreateGroupRequest(name, memberIds)); _rooms.value = listOf(resp.room) + _rooms.value; openRoom(resp.room) }
            catch (e: Exception) { _toastMsg.value = "Group nahi bana" }
        }
    }

    fun inviteToGroup(roomId: String, userId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.groupInvite(token, com.ytsubexchange.data.GroupInviteRequest(roomId, userId))
                _toastMsg.value = "Invite bhej diya ✓"
            } catch (e: Exception) { _toastMsg.value = "Invite nahi bhej saka" }
        }
    }

    fun acceptGroupInvite(roomId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.groupAccept(token, com.ytsubexchange.data.GroupAcceptRequest(roomId))
                clearGroupInvite()
                // Reload rooms then open the group
                val resp = RetrofitClient.api.getChatRooms(token)
                _rooms.value = resp.rooms.sortedByDescending { it.lastTime ?: "" }
                val room = resp.rooms.firstOrNull { it._id == roomId }
                _toastMsg.value = "Group join kar liya! 🎉"
                if (room != null) openRoom(room)
            } catch (e: Exception) { _toastMsg.value = "Join nahi ho saka" }
        }
    }

    fun rejectGroupInvite(roomId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.groupReject(token, com.ytsubexchange.data.GroupRejectRequest(roomId))
                clearGroupInvite()
            } catch (e: Exception) { }
        }
    }

    fun loadGroupInfo(roomId: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getGroupInfo(token, roomId)
                _groupInfo.value = resp
                _showGroupInfo.value = true
            } catch (e: Exception) { _toastMsg.value = "Group info load nahi hua" }
        }
    }

    fun hideGroupInfo() { _showGroupInfo.value = false }

    // ── Group Search ──────────────────────────────────────────
    private val _groupSearchResults = MutableStateFlow<List<com.ytsubexchange.data.GroupSearchResult>>(emptyList())
    val groupSearchResults: StateFlow<List<com.ytsubexchange.data.GroupSearchResult>> = _groupSearchResults

    fun searchGroups(query: String) {
        if (query.length < 2) { _groupSearchResults.value = emptyList(); return }
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.searchGroups(token, query)
                _groupSearchResults.value = resp.groups
            } catch (e: Exception) { }
        }
    }

    fun joinGroupByLink(inviteToken: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.joinGroupByLink(token, mapOf("token" to inviteToken))
                if (resp.success) {
                    _toastMsg.value = if (resp.alreadyMember) "Aap pehle se is group mein hain" else "Group join kar liya! 🎉"
                    loadRooms()
                    resp.room?.let { openRoom(it) }
                }
            } catch (e: Exception) { _toastMsg.value = "Join nahi ho saka: ${e.message?.take(40)}" }
        }
    }

    // ── Forward / Disappearing / Read ────────────────────────
    fun forwardMessage(msgId: String, targetRoomIds: List<String>) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.forwardMessage(token, mapOf("msgId" to msgId, "targetRoomIds" to targetRoomIds))
                _toastMsg.value = "Message forward ho gaya ✅"
            } catch (e: Exception) { _toastMsg.value = "Forward nahi hua" }
        }
    }

    fun setDisappearing(roomId: String, seconds: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.setDisappearing(token, com.ytsubexchange.data.SetDisappearingRequest(roomId, seconds))
                _toastMsg.value = when (seconds) {
                    0 -> "Auto-delete off ✓"
                    1 -> "Messages dekhe ke baad delete honge ✓"
                    86400 -> "Messages 24 hours baad delete honge ✓"
                    604800 -> "Messages 7 days baad delete honge ✓"
                    else -> "Auto-delete set ho gaya ✅"
                }
                _rooms.value = _rooms.value.map { if (it._id == roomId) it.copy(disappearingSeconds = seconds) else it }
                _openRoom.value = _openRoom.value?.let { if (it._id == roomId) it.copy(disappearingSeconds = seconds) else it }
            } catch (e: Exception) { _toastMsg.value = "Set nahi hua: ${e.message?.take(30)}" }
        }
    }

    fun markRoomRead(roomId: String) {
        viewModelScope.launch {
            try { RetrofitClient.api.markRead(token, mapOf("roomId" to roomId)) } catch (e: Exception) { }
        }
    }

    fun updateGroupInfo(roomId: String, name: String, description: String) {
        viewModelScope.launch {
            try {
                val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                val roomIdBody = roomId.toRequestBody("text/plain".toMediaTypeOrNull())
                RetrofitClient.api.updateGroupSettings(token, roomIdBody, nameBody, descBody, null)
                _toastMsg.value = "Group info update ho gaya ✅"
                loadGroupInfo(roomId)
                loadRooms()
            } catch (e: Exception) { _toastMsg.value = "Update nahi hua" }
        }
    }

    fun getGroupInviteLink(roomId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getGroupInviteLink(token, mapOf("roomId" to roomId))
                onResult(resp.inviteLink)
            } catch (e: Exception) { _toastMsg.value = "Link generate nahi hua" }
        }
    }

    fun setSlowMode(roomId: String, seconds: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.setSlowMode(token, mapOf("roomId" to roomId, "seconds" to seconds))
                _toastMsg.value = if (seconds == 0) "Slow mode off" else "Slow mode: ${seconds}s ✅"
            } catch (e: Exception) { _toastMsg.value = "Slow mode set nahi hua" }
        }
    }

    fun manageSubAdmin(roomId: String, targetUserId: String, action: String, permissions: Map<String, Boolean>? = null) {
        viewModelScope.launch {
            try {
                val perms = permissions?.let {
                    com.ytsubexchange.data.SubAdminPermissions(
                        canDeleteMessages = it["canDeleteMessages"] ?: true,
                        canBanMembers = it["canBanMembers"] ?: false,
                        canInviteMembers = it["canInviteMembers"] ?: true,
                        canPinMessages = it["canPinMessages"] ?: false,
                        canChangeGroupInfo = it["canChangeGroupInfo"] ?: false
                    )
                }
                val body = com.ytsubexchange.data.ManageSubAdminRequest(roomId, targetUserId, action, perms)
                RetrofitClient.api.manageSubAdmin(token, body)
                _toastMsg.value = when (action) {
                    "add" -> "Sub-admin bana diya ⭐"
                    "remove" -> "Sub-admin role hataya"
                    else -> "Updated"
                }
                loadGroupInfo(roomId)
            } catch (e: Exception) { _toastMsg.value = "Sub-admin update nahi hua: ${e.message?.take(50)}" }
        }
    }

    fun removeMemberFromGroup(roomId: String, userId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.groupRemoveMember(token, com.ytsubexchange.data.GroupRemoveRequest(roomId, userId))
                loadGroupInfo(roomId)
                _toastMsg.value = "Member remove kar diya"
            } catch (e: Exception) { _toastMsg.value = "Remove nahi ho saka" }
        }
    }

    fun leaveGroup(onDone: () -> Unit) {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.groupLeave(token, com.ytsubexchange.data.GroupLeaveRequest(roomId))
                _toastMsg.value = "Group leave kar diya"
                closeRoom()
                loadRooms()
                onDone()
            } catch (e: Exception) { _toastMsg.value = "Leave nahi ho saka" }
        }
    }

    fun clearToast() { _toastMsg.value = null }

    fun clearChat() {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.clearChat(token, mapOf("roomId" to roomId))
                _messages.value = emptyList()
                _pinnedMsg.value = null
                _toastMsg.value = "Chat clear ho gaya"
            } catch (e: Exception) { _toastMsg.value = "Clear nahi ho saka" }
        }
    }

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _isBlockedByMe = MutableStateFlow(false)
    val isBlockedByMe: StateFlow<Boolean> = _isBlockedByMe

    private val _isBlockedByThem = MutableStateFlow(false)
    val isBlockedByThem: StateFlow<Boolean> = _isBlockedByThem

    private val _blockedUsers = MutableStateFlow<List<com.ytsubexchange.data.BlockedUser>>(emptyList())
    val blockedUsers: StateFlow<List<com.ytsubexchange.data.BlockedUser>> = _blockedUsers

    fun toggleMute() {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.muteChat(token, mapOf("roomId" to roomId))
                _isMuted.value = resp.muted
                _toastMsg.value = if (resp.muted) "Notifications mute ho gayi 🔕" else "Notifications unmute ho gayi 🔔"
            } catch (e: Exception) { _toastMsg.value = "Mute nahi ho saka" }
        }
    }

    fun blockUser(onDone: () -> Unit) {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.blockUser(token, mapOf("roomId" to roomId))
                _isBlockedByMe.value = true
                _openRoom.value = _openRoom.value?.copy(isBlockedByMe = true)
                // Remove from chat list — blocked user should not appear
                _rooms.value = _rooms.value.filter { it._id != roomId }
                _toastMsg.value = "User block ho gaya 🚫"
                loadBlockedUsers()
                onDone()
            } catch (e: Exception) { _toastMsg.value = "Block nahi ho saka" }
        }
    }

    fun unblockUser(onDone: () -> Unit) {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.unblockUser(token, mapOf("roomId" to roomId))
                _isBlockedByMe.value = false
                _openRoom.value = _openRoom.value?.copy(isBlockedByMe = false)
                _toastMsg.value = "User unblock ho gaya ✅"
                loadBlockedUsers()
                loadRooms() // Reload to show unblocked user's room again
                onDone()
            } catch (e: Exception) { _toastMsg.value = "Unblock nahi ho saka" }
        }
    }

    fun unblockUserById(userId: String, roomId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.unblockUser(token, mapOf("roomId" to roomId))
                _blockedUsers.value = _blockedUsers.value.filter { it.userId != userId }
                _toastMsg.value = "User unblock ho gaya ✅"
                loadRooms() // Reload to show unblocked user's room
            } catch (e: Exception) { _toastMsg.value = "Unblock nahi ho saka" }
        }
    }

    fun loadBlockedUsers() {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getBlockedUsers(token)
                _blockedUsers.value = resp.blockedUsers
            } catch (e: Exception) { }
        }
    }

    fun joinCommunity() {
        if (inCommunity) return
        inCommunity = true
        SocketManager.emit("join_community", JSONObject())
        viewModelScope.launch {
            try { val resp = RetrofitClient.api.getCommunityMessages(token); _communityMessages.value = resp.messages } catch (e: Exception) { }
        }
    }

    fun sendCommunityMessage(text: String) {
        val tempMsg = ChatMessage(
            _id = "temp_${System.currentTimeMillis()}", senderId = myId, senderName = "Me", senderPic = "", text = text,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()).format(java.util.Date())
        )
        _communityMessages.value = _communityMessages.value + tempMsg
        // Sirf socket se bhejo — server socket handler DB mein save karta hai
        SocketManager.emit("community_message", JSONObject().apply { put("text", text) })
    }

    private fun handleIncomingMessage(data: JSONObject, isNotify: Boolean) {
        // Parse replyTo
        val replyTo = data.optJSONObject("replyTo")?.let { r ->
            ReplyRef(
                msgId = r.optString("msgId"),
                text = r.optString("text"),
                senderName = r.optString("senderName")
            )
        }
        val msg = ChatMessage(
            _id = data.optString("_id"),
            senderId = data.optString("senderId"),
            senderName = data.optString("senderName"),
            senderPic = data.optString("senderPic"),
            text = data.optString("text"),
            createdAt = data.optString("createdAt"),
            replyTo = replyTo,
            fileUrl = data.optString("fileUrl").takeIf { it.isNotEmpty() },
            fileType = data.optString("fileType").takeIf { it.isNotEmpty() },
            fileName = data.optString("fileName").takeIf { it.isNotEmpty() }
        )
        val roomId = data.optString("roomId")

        // myId set karo agar temp message match kare
        if (_myIdFlow.value.isEmpty()) {
            val tempMatch = _messages.value.any { it._id.startsWith("temp_") && it.text == msg.text }
            if (tempMatch) {
                _myIdFlow.value = msg.senderId
                prefs.edit().putString("userId", msg.senderId).apply()
            }
        }

        val myId = _myIdFlow.value
        val isMyMsg = myId.isNotEmpty() && msg.senderId == myId
        val isCurrentRoom = roomId == _openRoom.value?._id

        // Duplicate check — chat_message aur chat_message_notify dono aa sakte hain
        val alreadyInCache = (_messageCache[roomId] ?: emptyList()).any { it._id == msg._id }

        if (isCurrentRoom && !isNotify) {
            // Room open hai aur direct chat_message — temp replace karo ya add karo
            val updated = if (isMyMsg) {
                // Agar _id already list mein hai (REST response se already add ho chuka) toh skip
                if (_messages.value.any { it._id == msg._id }) {
                    _messages.value
                } else {
                    val withoutTemp = _messages.value.filter { !(it._id.startsWith("temp_") && it.text == msg.text) }
                    // Preserve replyTo from temp message if server didn't send it
                    val tempReplyTo = _messages.value.firstOrNull { it._id.startsWith("temp_") && it.text == msg.text }?.replyTo
                    val finalMsg = if (msg.replyTo == null && tempReplyTo != null) msg.copy(replyTo = tempReplyTo) else msg
                    withoutTemp + finalMsg
                }
            } else {
                if (_messages.value.any { it._id == msg._id }) _messages.value else _messages.value + msg
            }
            _messages.value = updated
            _messageCache[roomId] = updated
            saveMessagesToDisk(roomId, updated)
        } else if (isCurrentRoom && isNotify) {
            // Room open hai lekin notify event aaya — sirf cache update, NO popup
            if (!alreadyInCache) {
                val updated = _messages.value.let {
                    if (it.any { m -> m._id == msg._id }) it else it + msg
                }
                _messages.value = updated
                _messageCache[roomId] = updated
                saveMessagesToDisk(roomId, updated)
            }
        } else {
            // Room open nahi hai
            // Popup sirf tab: isNotify=true (server confirmed we're not in chat room),
            // apna message nahi, aur pehle se cache mein nahi
            if (isNotify && !isMyMsg && !alreadyInCache) {
                val room = _rooms.value.firstOrNull { it._id == roomId }
                if (room != null) {
                    _inAppNotif.value = InAppNotif(
                        senderName = if (room.isGroup) "${room.name}: ${msg.senderName}" else msg.senderName,
                        text = msg.text,
                        roomId = roomId,
                        room = room
                    )
                }
            }
            // Cache mein add karo (duplicate nahi)
            if (!alreadyInCache) {
                val updated = (_messageCache[roomId] ?: emptyList()) + msg
                _messageCache[roomId] = updated
                saveMessagesToDisk(roomId, updated)
            }
        }

        // Room list update karo — updated room ko top pe laao (WhatsApp style)
        val updatedRooms = _rooms.value.map {
            if (it._id == roomId) it.copy(
                lastMessage = msg.text,
                lastTime = msg.createdAt,
                // Unread rules:
                // - isCurrentRoom = seen = 0
                // - isMyMsg = apna message = 0
                // - isNotify = true means server confirmed user was NOT in chat room socket
                //   → this is the authoritative "you missed this" signal → +1
                // - chat_message with room not open = stale socket join, don't double-count
                unread = when {
                    isCurrentRoom -> 0
                    isMyMsg -> it.unread
                    isNotify -> it.unread + 1   // only notify events count as unread
                    else -> it.unread           // chat_message when room not open = stale, skip
                }
            ) else it
        }
        // Sort by lastTime descending so latest message room comes first
        _rooms.value = updatedRooms.sortedByDescending { it.lastTime ?: "" }
    }

    private fun listenSocketEvents() {
    SocketManager.on("chat_message") { args ->
            try {
                val data = args[0] as JSONObject
                handleIncomingMessage(data, isNotify = false)
            } catch (e: Exception) { }
        }

        // chat_message_notify — server sends this to user's personal room
        // Triggers when user is offline/in different room — for popup + cache
        SocketManager.on("chat_message_notify") { args ->
            try {
                val data = args[0] as JSONObject
                handleIncomingMessage(data, isNotify = true)
            } catch (e: Exception) { }
        }

        SocketManager.on("chat_typing") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val typing = data.optBoolean("typing")
                if (roomId == _openRoom.value?._id) {
                    _isTyping.value = typing
                    if (typing) { typingJob?.cancel(); typingJob = viewModelScope.launch { kotlinx.coroutines.delay(3000); _isTyping.value = false } }
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("community_message") { args ->
            try {
                val data = args[0] as JSONObject
                val msg = ChatMessage(_id = data.optString("_id", "c_${System.currentTimeMillis()}"),
                    senderId = data.optString("senderId"), senderName = data.optString("senderName"),
                    senderPic = data.optString("senderPic"), text = data.optString("text"), createdAt = data.optString("createdAt"))
                _communityMessages.value = _communityMessages.value.filter { !it._id.startsWith("temp_") || it.text != msg.text } + msg
            } catch (e: Exception) { }
        }

        SocketManager.on("community_status") { args ->
            try {
                val data = args[0] as JSONObject
                _communityOpen.value = data.optBoolean("isOpen", true)
                val pinned = data.optString("pinned", "")
                if (pinned.isNotEmpty()) _communityPinned.value = pinned
                val count = data.optInt("onlineCount", 0)
                if (count > 0) _communityOnline.value = count
            } catch (e: Exception) { }
        }

        SocketManager.on("community_online_count") { args ->
            try {
                val data = args[0] as JSONObject
                _communityOnline.value = data.optInt("count", 0)
            } catch (e: Exception) { }
        }

        SocketManager.on("community_pinned") { args ->
            try {
                val data = args[0] as JSONObject
                _communityPinned.value = data.optString("message", "")
            } catch (e: Exception) { }
        }

        SocketManager.on("community_message_deleted") { args ->
            try {
                val data = args[0] as JSONObject
                val msgId = data.optString("msgId")
                _communityMessages.value = _communityMessages.value.filter { it._id != msgId }
            } catch (e: Exception) { }
        }

        SocketManager.on("community_cleared") { _ ->
            _communityMessages.value = emptyList()
        }

        SocketManager.on("community_error") { args ->
            try {
                val data = args[0] as JSONObject
                _toastMsg.value = data.optString("message", "Community error")
            } catch (e: Exception) { }
        }

        SocketManager.on("slow_mode_wait") { args ->
            try {
                val data = args[0] as JSONObject
                val wait = data.optInt("waitSeconds", 0)
                _toastMsg.value = "⏳ Slow mode: ${wait}s baad message bhejo"
            } catch (e: Exception) { }
        }

        SocketManager.on("group_info_updated") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val name = data.optString("name")
                val pic = data.optString("pic")
                _rooms.value = _rooms.value.map {
                    if (it._id == roomId) it.copy(name = name.ifEmpty { it.name }, pic = pic.ifEmpty { it.pic ?: "" }) else it
                }
                if (_openRoom.value?._id == roomId) {
                    _openRoom.value = _openRoom.value?.copy(name = name.ifEmpty { _openRoom.value!!.name }, pic = pic.ifEmpty { _openRoom.value!!.pic ?: "" })
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("disappearing_changed") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val seconds = data.optInt("seconds", 0)
                _rooms.value = _rooms.value.map { if (it._id == roomId) it.copy(disappearingSeconds = seconds) else it }
                _openRoom.value = _openRoom.value?.let { if (it._id == roomId) it.copy(disappearingSeconds = seconds) else it }
            } catch (e: Exception) { }
        }

        SocketManager.on("messages_read") { args ->
            try {
                val data = args[0] as JSONObject
                val readBy = data.optString("readBy")
                // Update messages to show read status
                _messages.value = _messages.value.map { msg ->
                    if (msg.senderId == myId && !msg.readBy.contains(readBy)) {
                        msg.copy(readBy = msg.readBy + readBy, read = true)
                    } else msg
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("messages_disappeared") { args ->
            try {
                val data = args[0] as JSONObject
                val arr = data.optJSONArray("msgIds") ?: return@on
                val ids = (0 until arr.length()).map { arr.getString(it) }.toSet()
                _messages.value = _messages.value.filter { !ids.contains(it._id) }
            } catch (e: Exception) { }
        }

        SocketManager.on("chat_request") { args ->
            try {
                val data = args[0] as JSONObject
                val req = com.ytsubexchange.data.ChatRequest(
                    requestId = data.optString("requestId"),
                    fromUserId = data.optString("fromUserId"),
                    fromName = data.optString("fromName"),
                    fromPic = data.optString("fromPic"),
                    createdAt = data.optString("createdAt")
                )
                // Duplicate check
                if (_pendingRequests.value.none { it.requestId == req.requestId }) {
                    _pendingRequests.value = listOf(req) + _pendingRequests.value
                }
                // Toast sirf background mein ho toh dikhao — dialog already show hoga
            } catch (e: Exception) { }
        }

        SocketManager.on("chat_request_accepted") { args ->
            try {
                val data = args[0] as JSONObject
                val requestId = data.optString("requestId")
                val roomData = data.optJSONObject("room")
                if (roomData != null) {
                    val room = ChatRoom(
                        _id = roomData.optString("_id"),
                        name = roomData.optString("name"),
                        pic = roomData.optString("pic"),
                        isGroup = false,
                        lastMessage = "",
                        lastTime = ""
                    )
                    _sentRequests.value = _sentRequests.value.filter { it.requestId != requestId }
                    _rooms.value = listOf(room) + _rooms.value.filter { it._id != room._id }
                    // Sender ko in-app popup dikhao (chat notification style)
                    _inAppNotif.value = InAppNotif(
                        senderName = room.name,
                        text = "✅ Chat request accept ho gayi! Tap karo chat karne ke liye",
                        roomId = room._id,
                        room = room
                    )
                }
                viewModelScope.launch {
                    try {
                        val resp = RetrofitClient.api.getSentRequests(token)
                        _sentRequests.value = resp.requests
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("chat_request_rejected") { args ->
            try {
                val data = args[0] as JSONObject
                val requestId = data.optString("requestId")
                // Silently remove from sent requests list
                _sentRequests.value = _sentRequests.value.filter { it.requestId != requestId }
                viewModelScope.launch {
                    try {
                        val resp = RetrofitClient.api.getSentRequests(token)
                        _sentRequests.value = resp.requests
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("group_invite") { args ->
            try {
                val data = args[0] as JSONObject
                _groupInvite.value = com.ytsubexchange.data.GroupInviteNotif(
                    roomId = data.optString("roomId"),
                    roomName = data.optString("roomName"),
                    invitedBy = data.optString("invitedBy"),
                    invitedByPic = data.optString("invitedByPic")
                )
            } catch (e: Exception) { }
        }

        SocketManager.on("group_removed") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val roomName = data.optString("roomName")
                _rooms.value = _rooms.value.filter { it._id != roomId }
                if (_openRoom.value?._id == roomId) closeRoom()
                _toastMsg.value = "Aapko '$roomName' group se remove kar diya gaya"
            } catch (e: Exception) { }
        }

        SocketManager.on("group_member_joined") { args ->
            try {
                loadRooms()
            } catch (e: Exception) { }
        }

        SocketManager.on("user_status") { args ->
            try {
                val data = args[0] as JSONObject
                val userId = data.optString("userId")
                val online = data.optBoolean("online")
                if (userId.isNotEmpty()) {
                    _onlineUsers.value = if (online) {
                        _onlineUsers.value + userId
                    } else {
                        _onlineUsers.value - userId
                    }
                }
            } catch (e: Exception) { }
        }

        // Server se initial online partners list (join_user_room ke response mein)
        SocketManager.on("online_partners") { args ->
            try {
                val data = args[0] as JSONObject
                val arr = data.optJSONArray("onlineUsers") ?: return@on
                val ids = (0 until arr.length()).map { arr.getString(it) }.toSet()
                if (ids.isNotEmpty()) {
                    _onlineUsers.value = _onlineUsers.value + ids
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("user_blocked") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val blockedBy = data.optString("blockedBy")
                val myUserId = _myIdFlow.value
                if (roomId.isNotEmpty()) {
                    if (blockedBy == myUserId) {
                        // Maine block kiya
                        _isBlockedByMe.value = true
                        _openRoom.value = _openRoom.value?.copy(isBlockedByMe = true)
                    } else {
                        // Unhone mujhe block kiya
                        _isBlockedByThem.value = true
                        _openRoom.value = _openRoom.value?.copy(isBlockedByThem = true)
                        _toastMsg.value = "Is user ne aapko block kar diya"
                    }
                }
            } catch (e: Exception) { }
        }

        SocketManager.on("user_unblocked") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val unblockedBy = data.optString("unblockedBy")
                val myUserId = _myIdFlow.value
                if (roomId.isNotEmpty()) {
                    if (unblockedBy == myUserId) {
                        _isBlockedByMe.value = false
                        _openRoom.value = _openRoom.value?.copy(isBlockedByMe = false)
                    } else {
                        _isBlockedByThem.value = false
                        _openRoom.value = _openRoom.value?.copy(isBlockedByThem = false)
                        _toastMsg.value = "Is user ne aapko unblock kar diya ✅"
                    }
                }
            } catch (e: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocketManager.off("chat_message")
        SocketManager.off("chat_message_notify")
        SocketManager.off("chat_typing")
        SocketManager.off("community_message")
        SocketManager.off("chat_request")
        SocketManager.off("chat_request_accepted")
        SocketManager.off("group_invite")
        SocketManager.off("group_removed")
        SocketManager.off("group_member_joined")
        SocketManager.off("user_status")
        SocketManager.off("online_partners")
        SocketManager.off("user_blocked")
        SocketManager.off("user_unblocked")
        SocketManager.off("community_status")
        SocketManager.off("community_online_count")
        SocketManager.off("community_pinned")
        SocketManager.off("community_message_deleted")
        SocketManager.off("community_cleared")
        SocketManager.off("community_error")
    }
}
