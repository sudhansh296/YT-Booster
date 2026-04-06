package com.ytsubexchange.test.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ytsubexchange.test.data.ChatMessage
import com.ytsubexchange.test.data.ChatRoom
import com.ytsubexchange.test.data.ChatUser
import com.ytsubexchange.test.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

private val avatarGradients = listOf(
    listOf(Color(0xFF7B2FF7), Color(0xFF4A1A8A)),
    listOf(Color(0xFFE67E22), Color(0xFFD35400)),
    listOf(Color(0xFF27AE60), Color(0xFF1A6B3A)),
    listOf(Color(0xFF2980B9), Color(0xFF1A5276)),
    listOf(Color(0xFFE91E8C), Color(0xFF8B0057)),
    listOf(Color(0xFF546E7A), Color(0xFF263238)),
)
private fun avatarGradient(index: Int): Brush =
    Brush.linearGradient(avatarGradients[index % avatarGradients.size], Offset(0f,0f), Offset(100f,100f))

private val BgDark      = Color(0xFF0D0D0D)
private val CardDark    = Color(0xFF1A1A2E)
private val CardAlt     = Color(0xFF16213E)
private val AccentRed   = Color(0xFFE53935)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSec     = Color(0xFF9E9E9E)
private val Divider2    = Color(0xFF1E1E2E)
private val SearchBg    = Color(0xFF1A1A2E)

@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit, openCommunity: Boolean = false) {
    val rooms by viewModel.rooms.collectAsState()
    val openRoom by viewModel.openRoom.collectAsState()
    val toastMsg by viewModel.toastMsg.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMsg) { toastMsg?.let { snackbarHostState.showSnackbar(it); viewModel.clearToast() } }
    Box(Modifier.fillMaxSize().background(BgDark)) {
        if (openRoom != null) {
            ChatWindowScreen(room = openRoom!!, viewModel = viewModel, context = context, onBack = { viewModel.closeRoom() })
        } else {
            ChatListScreen(rooms = rooms, onRoomClick = { viewModel.openRoom(it) }, onBack = onBack, viewModel = viewModel, openCommunity = openCommunity)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun ChatListScreen(rooms: List<ChatRoom>, onRoomClick: (ChatRoom) -> Unit, onBack: () -> Unit, viewModel: ChatViewModel, openCommunity: Boolean = false) {
    val isLoading by viewModel.isLoading.collectAsState()
    val showNewChat by viewModel.showNewChat.collectAsState()
    val showCreateGroup by viewModel.showCreateGroup.collectAsState()
    val users by viewModel.users.collectAsState()
    var selectedTab by remember { mutableStateOf(if (openCommunity) 2 else 0) }
    val filtered = rooms.filter { if (selectedTab == 0) !it.isGroup else it.isGroup }

    Column(Modifier.fillMaxSize().background(BgDark)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(AccentRed, Color(0xFFFF6B6B)))))
        Box(Modifier.fillMaxWidth().padding(12.dp, 8.dp).clip(RoundedCornerShape(24.dp)).background(SearchBg).clickable { viewModel.showNewChatDialog() }) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = TextSec, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("User search karo...", color = TextSec, fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Direct", "Group", "Community").forEachIndexed { i, label ->
                Column(Modifier.clickable { selectedTab = i }.padding(10.dp, 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = if (selectedTab == i) AccentRed else TextSec, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        if (i == 2) {
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(AccentRed).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (selectedTab == i) Box(Modifier.width(40.dp).height(2.dp).background(AccentRed))
                }
            }
            Spacer(Modifier.weight(1f))
            if (selectedTab != 2) {
                IconButton(onClick = { if (selectedTab == 1) viewModel.showCreateGroupDialog() else viewModel.showNewChatDialog() }) {
                    Icon(Icons.Default.Add, null, tint = AccentRed, modifier = Modifier.size(22.dp))
                }
            }
        }
        Divider(color = Divider2, thickness = 0.5.dp)
        when (selectedTab) {
            2 -> CommunityChatScreen(viewModel = viewModel)
            else -> {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentRed) }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Koi chat nahi", color = TextSec, fontSize = 14.sp) }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered.size) { i ->
                            ChatRoomRow(room = filtered[i], avatarIndex = i, onClick = { onRoomClick(filtered[i]) })
                            Divider(color = Divider2, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
    if (showNewChat) NewChatDialog(users, { viewModel.loadUsers(it) }, { viewModel.startDm(it) }, { viewModel.showCreateGroupDialog() }, { viewModel.hideNewChatDialog() })
    if (showCreateGroup) CreateGroupDialog(users, { viewModel.loadUsers(it) }, { n, ids -> viewModel.createGroup(n, ids) }, { viewModel.hideCreateGroupDialog() })
}

@Composable
fun CommunityChatScreen(viewModel: ChatViewModel) {
    val communityMessages by viewModel.communityMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { viewModel.joinCommunity() }
    LaunchedEffect(communityMessages.size) { if (communityMessages.isNotEmpty()) listState.animateScrollToItem(communityMessages.size - 1) }
    Column(Modifier.fillMaxSize().background(BgDark)) {
        Row(Modifier.fillMaxWidth().background(CardDark).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = AccentRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Community Chat", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(AccentRed).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Divider(color = Divider2, thickness = 0.5.dp)
        if (communityMessages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Koi message nahi abhi", color = TextSec, fontSize = 14.sp) }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(communityMessages) { msg -> MessageBubble(msg = msg, myId = viewModel.myId, onLongClick = {}) }
            }
        }
        Row(Modifier.fillMaxWidth().background(CardDark).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, null, tint = TextSec, modifier = Modifier.size(20.dp)) }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(SearchBg).padding(14.dp, 10.dp)) {
                if (inputText.isEmpty()) Text("Message...", color = TextSec, fontSize = 14.sp)
                BasicTextField(value = inputText, onValueChange = { inputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) { viewModel.sendCommunityMessage(inputText.trim()); inputText = "" } }),
                    maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B)))), contentAlignment = Alignment.Center) {
                IconButton(onClick = { if (inputText.isNotBlank()) { viewModel.sendCommunityMessage(inputText.trim()); inputText = "" } }) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChatRoomRow(room: ChatRoom, avatarIndex: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).background(BgDark).padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            if (room.pic.isNotEmpty()) {
                AsyncImage(model = room.pic, contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(52.dp).clip(CircleShape).background(avatarGradient(avatarIndex)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(28.dp))
                }
            }
            Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF4CAF50)).align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(room.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(formatChatTime(room.lastTime), color = TextSec, fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(room.lastMessage.ifEmpty { "Tap to chat" }, color = TextSec, fontSize = 13.sp, maxLines = 1)
        }
        if (room.unread > 0) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(AccentRed), contentAlignment = Alignment.Center) {
                Text(if (room.unread > 9) "9+" else "${room.unread}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWindowScreen(room: ChatRoom, viewModel: ChatViewModel, context: Context, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val pinnedMsg by viewModel.pinnedMsg.collectAsState()
    val contextMsg by viewModel.contextMsg.collectAsState()
    val showStarred by viewModel.showStarred.collectAsState()
    val showPinned by viewModel.showPinned.collectAsState()
    val starredMessages by viewModel.starredMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    val displayMessages = if (showSearchBar && searchQuery.isNotBlank()) messages.filter { it.text.contains(searchQuery, ignoreCase = true) } else messages

    Column(Modifier.fillMaxSize().background(BgDark)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(AccentRed, Color(0xFFFF6B6B)))))
        Row(Modifier.fillMaxWidth().background(CardDark).padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
            Box(Modifier.size(38.dp).clip(CircleShape).background(avatarGradient(0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (isTyping) "typing..." else "online", color = if (isTyping) AccentRed else Color(0xFF4CAF50), fontSize = 11.sp)
            }
            IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = TextSec) }
            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, modifier = Modifier.background(CardDark), offset = androidx.compose.ui.unit.DpOffset(x = (-160).dp, y = 0.dp)) {
                listOf("Search in Chat", "Starred Messages", "Pinned Messages", "Media and Files", "Mute Notifications", "Clear Chat", "Block User").forEach { item ->
                    DropdownMenuItem(text = { Text(item, color = TextPrimary, fontSize = 14.sp) }, onClick = {
                        showMoreMenu = false
                        when (item) {
                            "Search in Chat" -> { showSearchBar = !showSearchBar; if (!showSearchBar) searchQuery = "" }
                            "Starred Messages" -> viewModel.showStarredPanel()
                            "Pinned Messages" -> viewModel.showPinnedPanel()
                        }
                    })
                }
            }
        }
        if (showSearchBar) {
            Row(Modifier.fillMaxWidth().background(CardAlt).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(SearchBg).padding(14.dp, 8.dp)) {
                    if (searchQuery.isEmpty()) Text("Search in chat...", color = TextSec, fontSize = 13.sp)
                    BasicTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = TextSec, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        pinnedMsg?.let { msg ->
            Row(Modifier.fillMaxWidth().background(CardAlt).padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(32.dp).background(AccentRed))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Pinned", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(msg.text.take(60), color = TextSec, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(displayMessages) { msg -> MessageBubble(msg = msg, myId = viewModel.myId, onLongClick = { viewModel.onMsgLongPress(msg) }) }
        }
        replyTo?.let { reply ->
            Row(Modifier.fillMaxWidth().background(CardAlt).padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(28.dp).background(AccentRed))
                Spacer(Modifier.width(8.dp))
                Text(reply.text.take(50), color = TextSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.cancelReply() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = TextSec, modifier = Modifier.size(16.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().background(CardDark).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📎", fontSize = 20.sp, modifier = Modifier.clickable {}.padding(4.dp))
            Text("🎤", fontSize = 20.sp, modifier = Modifier.clickable {}.padding(4.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(SearchBg).padding(14.dp, 10.dp)) {
                if (inputText.isEmpty()) Text("Message...", color = TextSec, fontSize = 14.sp)
                BasicTextField(value = inputText, onValueChange = { inputText = it; viewModel.onTyping(room._id, true) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) { viewModel.sendMessage(room._id, inputText.trim()); inputText = "" } }),
                    maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            Text("\uD83D\uDE0A", fontSize = 20.sp, modifier = Modifier.clickable {}.padding(4.dp))
            Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B)))), contentAlignment = Alignment.Center) {
                IconButton(onClick = { if (inputText.isNotBlank()) { viewModel.sendMessage(room._id, inputText.trim()); inputText = "" } }) {
                    if (inputText.isBlank()) Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    contextMsg?.let { msg ->
        Dialog(onDismissRequest = { viewModel.dismissContext() }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(8.dp)) {
                    listOf(
                        "Reply" to { viewModel.setReplyFromContext() },
                        "React" to { viewModel.reactToMessage("like") },
                        "Star" to { viewModel.starMessage() },
                        "Pin" to { viewModel.pinMessage() },
                        "Copy" to { val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("msg", msg.text)); viewModel.dismissContext() },
                        "Delete" to { viewModel.deleteMessage() }
                    ).forEach { (label, action) ->
                        TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                            Text(label, color = if (label == "Delete") AccentRed else TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    if (showStarred) {
        Dialog(onDismissRequest = { viewModel.hideStarredPanel() }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Starred Messages", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    if (starredMessages.isEmpty()) Text("Koi starred message nahi", color = TextSec, fontSize = 13.sp)
                    else LazyColumn { items(starredMessages) { m -> Text("- ${m.text}", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) } }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.hideStarredPanel() }, modifier = Modifier.align(Alignment.End)) { Text("Close", color = AccentRed) }
                }
            }
        }
    }
    if (showPinned) {
        Dialog(onDismissRequest = { viewModel.hidePinnedPanel() }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pinned Message", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    pinnedMsg?.let { Text(it.text, color = TextPrimary, fontSize = 13.sp) } ?: Text("Koi pinned message nahi", color = TextSec, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.hidePinnedPanel() }, modifier = Modifier.align(Alignment.End)) { Text("Close", color = AccentRed) }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage, myId: String, onLongClick: () -> Unit) {
    val isMine = msg.senderId == myId
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 270.dp)
            .clip(RoundedCornerShape(18.dp, 18.dp, if (isMine) 4.dp else 18.dp, if (isMine) 18.dp else 4.dp))
            .background(if (isMine) Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B))) else Brush.linearGradient(listOf(CardDark, CardAlt)))
            .clickable(onClick = onLongClick).padding(12.dp, 8.dp)) {
            if (!isMine) { Text(msg.senderName, color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(2.dp)) }
            msg.replyTo?.let { reply ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.2f)).padding(8.dp)) {
                    Text(reply.text.take(50), color = Color.White.copy(0.7f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(msg.text, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text(formatChatTime(msg.createdAt), color = Color.White.copy(0.6f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun NewChatDialog(users: List<ChatUser>, onSearch: (String) -> Unit, onDm: (ChatUser) -> Unit, onCreateGroup: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("New Chat", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SearchBg).padding(14.dp, 10.dp)) {
                    if (query.isEmpty()) Text("Search users...", color = TextSec, fontSize = 13.sp)
                    BasicTextField(value = query, onValueChange = { query = it; onSearch(it) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, tint = AccentRed); Spacer(Modifier.width(8.dp))
                    Text("Create Group", color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
                Divider(color = Divider2)
                LazyColumn(Modifier.weight(1f, false)) {
                    items(users.size) { i ->
                        Row(Modifier.fillMaxWidth().clickable { onDm(users[i]) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(avatarGradient(i)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                            }
                            Text(users[i].channelName, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = TextSec) }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(users: List<ChatUser>, onSearch: (String) -> Unit, onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var groupName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Create Group", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SearchBg).padding(14.dp, 10.dp)) {
                    if (groupName.isEmpty()) Text("Group name...", color = TextSec, fontSize = 13.sp)
                    BasicTextField(value = groupName, onValueChange = { groupName = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SearchBg).padding(14.dp, 10.dp)) {
                    if (query.isEmpty()) Text("Search members...", color = TextSec, fontSize = 13.sp)
                    BasicTextField(value = query, onValueChange = { query = it; onSearch(it) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(users.size) { i ->
                        val isSel = selected.contains(users[i]._id)
                        Row(Modifier.fillMaxWidth().clickable { if (isSel) selected.remove(users[i]._id) else selected.add(users[i]._id) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(checked = isSel, onCheckedChange = { if (it) selected.add(users[i]._id) else selected.remove(users[i]._id) },
                                colors = CheckboxDefaults.colors(checkedColor = AccentRed, uncheckedColor = TextSec))
                            Box(Modifier.size(32.dp).clip(CircleShape).background(avatarGradient(i)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
                            }
                            Text(users[i].channelName, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextSec) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (groupName.isNotBlank() && selected.isNotEmpty()) onCreate(groupName.trim(), selected.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        enabled = groupName.isNotBlank() && selected.isNotEmpty()) { Text("Create") }
                }
            }
        }
    }
}

fun formatChatTime(iso: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(iso) ?: return ""
        val diff = Date().time - date.time
        when {
            diff < 60_000 -> "now"
            diff < 3_600_000 -> "${diff / 60_000}m"
            diff < 86_400_000 -> "${diff / 3_600_000}h"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) { "" }
}







