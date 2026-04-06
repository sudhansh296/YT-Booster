package com.ytsubexchange.test.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytsubexchange.test.data.*
import com.ytsubexchange.test.network.RetrofitClient
import com.ytsubexchange.test.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val myId: String get() = prefs.getString("userId", "") ?: ""
    private val token: String get() = "Bearer ${prefs.getString("token", "")}"

    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val rooms: StateFlow<List<ChatRoom>> = _rooms

    private val _openRoom = MutableStateFlow<ChatRoom?>(null)
    val openRoom: StateFlow<ChatRoom?> = _openRoom

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

    // Context menu
    private val _contextMsg = MutableStateFlow<ChatMessage?>(null)
    val contextMsg: StateFlow<ChatMessage?> = _contextMsg

    // Starred messages panel
    private val _starredMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val starredMessages: StateFlow<List<ChatMessage>> = _starredMessages

    private val _showStarred = MutableStateFlow(false)
    val showStarred: StateFlow<Boolean> = _showStarred

    // Pinned messages panel
    private val _showPinned = MutableStateFlow(false)
    val showPinned: StateFlow<Boolean> = _showPinned

    // User search (for new DM / group)
    private val _users = MutableStateFlow<List<ChatUser>>(emptyList())
    val users: StateFlow<List<ChatUser>> = _users

    private val _showNewChat = MutableStateFlow(false)
    val showNewChat: StateFlow<Boolean> = _showNewChat

    private val _showCreateGroup = MutableStateFlow(false)
    val showCreateGroup: StateFlow<Boolean> = _showCreateGroup

    // Toast / snackbar message
    private val _toastMsg = MutableStateFlow<String?>(null)
    val toastMsg: StateFlow<String?> = _toastMsg

    // Community chat
    private val _communityMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val communityMessages: StateFlow<List<ChatMessage>> = _communityMessages
    private var inCommunity = false

    private var typingJob: kotlinx.coroutines.Job? = null

    init {
        loadRooms()
        listenSocketEvents()
    }

    fun loadRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = RetrofitClient.api.getChatRooms(token)
                _rooms.value = resp.rooms
            } catch (e: Exception) { /* offline */ } finally {
                _isLoading.value = false
            }
        }
    }

    fun openRoom(room: ChatRoom) {
        _openRoom.value = room
        _messages.value = emptyList()
        _pinnedMsg.value = null
        SocketManager.joinChatRoom(room._id)
        loadMessages(room._id)
    }

    fun closeRoom() {
        _openRoom.value?.let { SocketManager.leaveChatRoom(it._id) }
        _openRoom.value = null
        _messages.value = emptyList()
        _replyTo.value = null
        _pinnedMsg.value = null
        _showStarred.value = false
        _showPinned.value = false
    }

    private fun loadMessages(roomId: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getChatMessages(token, roomId)
                _messages.value = resp.messages
                // Set pinned if any
                _pinnedMsg.value = resp.messages.firstOrNull { it.pinned }
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun sendMessage(roomId: String, text: String) {
        val reply = _replyTo.value
        cancelReply()
        val tempMsg = ChatMessage(
            _id = "temp_${System.currentTimeMillis()}",
            senderId = myId,
            senderName = "Me",
            text = text,
            createdAt = java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()
            ).format(java.util.Date()),
            replyTo = reply
        )
        _messages.value = _messages.value + tempMsg
        SocketManager.sendChatMessage(roomId, text, reply?.msgId)
        viewModelScope.launch {
            try {
                RetrofitClient.api.sendMessage(token, SendMessageRequest(roomId, text, reply?.msgId))
            } catch (e: Exception) { /* socket already sent */ }
        }
        onTyping(roomId, false)
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

    // ── Context menu ──────────────────────────────────────────
    fun onMsgLongPress(msg: ChatMessage) { _contextMsg.value = msg }
    fun dismissContext() { _contextMsg.value = null }

    fun setReplyFromContext() {
        _contextMsg.value?.let {
            _replyTo.value = ReplyRef(msgId = it._id, text = it.text, senderName = it.senderName)
        }
        dismissContext()
    }

    fun cancelReply() { _replyTo.value = null }

    fun reactToMessage(emoji: String) {
        val msg = _contextMsg.value ?: return
        dismissContext()
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.reactToMessage(token, ReactRequest(msg._id, emoji))
                _messages.value = _messages.value.map {
                    if (it._id == msg._id) it.copy(reactions = resp.reactions) else it
                }
            } catch (e: Exception) { _toastMsg.value = "React failed" }
        }
    }

    fun starMessage() {
        val msg = _contextMsg.value ?: return
        dismissContext()
        viewModelScope.launch {
            try {
                RetrofitClient.api.starMessage(token, StarRequest(msg._id))
                _toastMsg.value = "Message starred ⭐"
            } catch (e: Exception) { _toastMsg.value = "Star failed" }
        }
    }

    fun pinMessage() {
        val msg = _contextMsg.value ?: return
        val roomId = _openRoom.value?._id ?: return
        dismissContext()
        viewModelScope.launch {
            try {
                RetrofitClient.api.pinMessage(token, PinRequest(msg._id, roomId))
                _pinnedMsg.value = msg
                _toastMsg.value = "Message pinned 📌"
            } catch (e: Exception) {
                // fallback: pin locally
                _pinnedMsg.value = msg
            }
        }
    }

    fun clearPin() { _pinnedMsg.value = null }

    fun deleteMessage() {
        val msg = _contextMsg.value ?: return
        dismissContext()
        viewModelScope.launch {
            try {
                RetrofitClient.api.deleteMessage(token, msg._id)
                _messages.value = _messages.value.filter { it._id != msg._id }
                _toastMsg.value = "Message deleted"
            } catch (e: Exception) { _toastMsg.value = "Delete failed" }
        }
    }

    fun copyMessage(): String {
        val text = _contextMsg.value?.text ?: ""
        dismissContext()
        return text
    }

    // ── Starred panel ─────────────────────────────────────────
    fun showStarredPanel() {
        val roomId = _openRoom.value?._id ?: return
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getStarredMessages(token, roomId)
                _starredMessages.value = resp.messages
                _showStarred.value = true
            } catch (e: Exception) { _toastMsg.value = "Could not load starred" }
        }
    }
    fun hideStarredPanel() { _showStarred.value = false }

    // ── Pinned panel ──────────────────────────────────────────
    fun showPinnedPanel() { _showPinned.value = true }
    fun hidePinnedPanel() { _showPinned.value = false }

    // ── New DM / Group ────────────────────────────────────────
    fun showNewChatDialog() {
        loadUsers("")
        _showNewChat.value = true
    }
    fun hideNewChatDialog() { _showNewChat.value = false }

    fun showCreateGroupDialog() {
        loadUsers("")
        _showCreateGroup.value = true
        _showNewChat.value = false
    }
    fun hideCreateGroupDialog() { _showCreateGroup.value = false }

    fun loadUsers(query: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getChatUsers(token, query)
                _users.value = resp.users
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun startDm(user: ChatUser) {
        _showNewChat.value = false
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.createDm(token, CreateDmRequest(user._id))
                val room = resp.room.copy(name = user.channelName, pic = user.profilePic)
                _rooms.value = listOf(room) + _rooms.value.filter { it._id != room._id }
                openRoom(room)
            } catch (e: Exception) { _toastMsg.value = "DM shuru nahi ho saka" }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        _showCreateGroup.value = false
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.createGroup(token, CreateGroupRequest(name, memberIds))
                _rooms.value = listOf(resp.room) + _rooms.value
                openRoom(resp.room)
            } catch (e: Exception) { _toastMsg.value = "Group nahi bana" }
        }
    }

    fun clearToast() { _toastMsg.value = null }

    // ── Community Chat ────────────────────────────────────────
    fun joinCommunity() {
        if (inCommunity) return
        inCommunity = true
        SocketManager.emit("join_community", JSONObject())
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.getCommunityMessages(token)
                _communityMessages.value = resp.messages
            } catch (e: Exception) { /* offline - socket se aayenge */ }
        }
    }

    fun sendCommunityMessage(text: String) {
        val tempMsg = ChatMessage(
            _id = "temp_${System.currentTimeMillis()}",
            senderId = myId,
            senderName = "Me",
            text = text,
            createdAt = java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()
            ).format(java.util.Date())
        )
        _communityMessages.value = _communityMessages.value + tempMsg
        SocketManager.emit("community_message", JSONObject().apply { put("text", text) })
        viewModelScope.launch {
            try {
                RetrofitClient.api.sendCommunityMessage(token, mapOf("text" to text))
            } catch (e: Exception) { /* socket already sent */ }
        }
    }

    // ── Socket events ─────────────────────────────────────────
    private fun listenSocketEvents() {
        SocketManager.on("chat_message") { args ->
            try {
                val data = args[0] as JSONObject
                val msg = ChatMessage(
                    _id = data.optString("_id"),
                    senderId = data.optString("senderId"),
                    senderName = data.optString("senderName"),
                    senderPic = data.optString("senderPic"),
                    text = data.optString("text"),
                    createdAt = data.optString("createdAt")
                )
                val roomId = data.optString("roomId")
                if (roomId == _openRoom.value?._id) {
                    _messages.value = _messages.value
                        .filter { !it._id.startsWith("temp_") || it.text != msg.text } + msg
                }
                _rooms.value = _rooms.value.map {
                    if (it._id == roomId) it.copy(
                        lastMessage = msg.text, lastTime = msg.createdAt,
                        unread = if (it._id == _openRoom.value?._id) 0 else it.unread + 1
                    ) else it
                }
            } catch (e: Exception) { /* ignore */ }
        }

        SocketManager.on("chat_typing") { args ->
            try {
                val data = args[0] as JSONObject
                val roomId = data.optString("roomId")
                val typing = data.optBoolean("typing")
                if (roomId == _openRoom.value?._id) {
                    _isTyping.value = typing
                    if (typing) {
                        typingJob?.cancel()
                        typingJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(3000)
                            _isTyping.value = false
                        }
                    }
                }
            } catch (e: Exception) { /* ignore */ }
        }

        SocketManager.on("community_message") { args ->
            try {
                val data = args[0] as JSONObject
                val msg = ChatMessage(
                    _id = data.optString("_id", "c_${System.currentTimeMillis()}"),
                    senderId = data.optString("senderId"),
                    senderName = data.optString("senderName"),
                    senderPic = data.optString("senderPic"),
                    text = data.optString("text"),
                    createdAt = data.optString("createdAt")
                )
                _communityMessages.value = _communityMessages.value
                    .filter { !it._id.startsWith("temp_") || it.text != msg.text } + msg
            } catch (e: Exception) { /* ignore */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocketManager.off("chat_message")
        SocketManager.off("chat_typing")
        SocketManager.off("community_message")
    }
}
