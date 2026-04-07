package com.ytsubexchange.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
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
import com.ytsubexchange.data.*
import com.ytsubexchange.viewmodel.CallViewModel
import com.ytsubexchange.viewmodel.ChatViewModel
import com.ytsubexchange.viewmodel.GroupVoiceChatViewModel
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
    Brush.linearGradient(avatarGradients[index % avatarGradients.size], Offset(0f, 0f), Offset(100f, 100f))

private val BgDark      = Color(0xFF0D0D0D)
private val CardDark    = Color(0xFF1A1A2E)
private val CardAlt     = Color(0xFF16213E)
private val AccentRed   = Color(0xFFE53935)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSec     = Color(0xFF9E9E9E)
private val Divider2    = Color(0xFF1E1E2E)
private val SearchBg    = Color(0xFF1A1A2E)

// Light mode colors
private val BgLight     = Color(0xFFF5F5F5)
private val CardLight   = Color(0xFFFFFFFF)
private val CardAltLight = Color(0xFFEEEEEE)
private val TextPrimaryLight = Color(0xFF1A1A1A)
private val TextSecLight = Color(0xFF666666)
private val Divider2Light = Color(0xFFDDDDDD)
private val SearchBgLight = Color(0xFFEEEEEE)

@Composable
private fun chatBg() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) BgDark else BgLight
@Composable
private fun chatCard() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) CardDark else CardLight
@Composable
private fun chatCardAlt() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) CardAlt else CardAltLight
@Composable
private fun chatTextPrimary() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) TextPrimary else TextPrimaryLight
@Composable
private fun chatTextSec() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) TextSec else TextSecLight
@Composable
private fun chatDivider() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) Divider2 else Divider2Light
@Composable
private fun chatSearchBg() = if (com.ytsubexchange.ui.theme.isDarkTheme.value) SearchBg else SearchBgLight

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit = {},
    callViewModel: CallViewModel? = null,
    voiceChatViewModel: GroupVoiceChatViewModel? = null,
    openCommunity: Boolean = false,
    onPickFile: (String, (android.net.Uri, String, String) -> Unit) -> Unit = { _, _ -> },
    onRequestScreenCapture: (() -> Unit)? = null,
) {
    val rooms by viewModel.rooms.collectAsState()
    val openRoom by viewModel.openRoom.collectAsState()
    val toastMsg by viewModel.toastMsg.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMsg) { toastMsg?.let { snackbarHostState.showSnackbar(it); viewModel.clearToast() } }

    Box(Modifier.fillMaxSize().background(chatBg()).statusBarsPadding()) {
        if (openRoom != null) {
            ChatWindowScreen(
                room = openRoom!!,
                viewModel = viewModel,
                context = context,
                onBack = { viewModel.closeRoom() },
                callViewModel = callViewModel,
                voiceChatViewModel = voiceChatViewModel,
                onPickFile = onPickFile,
                onRequestScreenCapture = onRequestScreenCapture
            )
        } else {
            ChatListScreen(
                rooms = rooms,
                onRoomClick = { viewModel.openRoom(it) },
                onBack = onBack,
                viewModel = viewModel,
                openCommunity = openCommunity
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun ChatListScreen(
    rooms: List<ChatRoom>,
    onRoomClick: (ChatRoom) -> Unit,
    onBack: () -> Unit,
    viewModel: ChatViewModel,
    openCommunity: Boolean = false
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val showNewChat by viewModel.showNewChat.collectAsState()
    val showCreateGroup by viewModel.showCreateGroup.collectAsState()
    val users by viewModel.users.collectAsState()
    val groupSearchResults by viewModel.groupSearchResults.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    var selectedTab by remember { mutableStateOf(if (openCommunity) 2 else 0) }
    val filtered = rooms.filter { r -> 
        (if (selectedTab == 0) !r.isGroup else r.isGroup) && !r.isBlockedByMe
    }

    val blockedUsers by viewModel.blockedUsers.collectAsState()
    var showChatMenu by remember { mutableStateOf(false) }
    var chatMenuScreen by remember { mutableStateOf("") } // "sent" | "received" | "blocked"

    var showRoomMenu by remember { mutableStateOf(false) }
    var menuRoom by remember { mutableStateOf<ChatRoom?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(chatBg())) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(AccentRed, Color(0xFFFF6B6B)))))
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).clickable {
                if (selectedTab == 1) {
                    viewModel.showNewChatDialog(); viewModel.searchGroups("")
                } else {
                    viewModel.showNewChatDialog()
                }
            }) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = chatTextSec(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedTab == 1) "Group search karo..." else "User search karo...", color = chatTextSec(), fontSize = 14.sp)
                }
            }
            Box {
                IconButton(onClick = { showChatMenu = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = chatTextSec())
                }
                DropdownMenu(
                    expanded = showChatMenu,
                    onDismissRequest = { showChatMenu = false },
                    modifier = Modifier.background(chatCard())
                ) {
                    DropdownMenuItem(
                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Text("Sent Requests", color = chatTextPrimary(), fontSize = 14.sp)
                            if (sentRequests.isNotEmpty()) Box(Modifier.size(18.dp).clip(CircleShape).background(AccentRed), contentAlignment = Alignment.Center) {
                                Text("${sentRequests.size}", color = Color.White, fontSize = 9.sp)
                            }
                        }},
                        onClick = { showChatMenu = false; chatMenuScreen = "sent" }
                    )
                    DropdownMenuItem(
                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallReceived, null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Text("Received Requests", color = chatTextPrimary(), fontSize = 14.sp)
                            if (pendingRequests.isNotEmpty()) Box(Modifier.size(18.dp).clip(CircleShape).background(AccentRed), contentAlignment = Alignment.Center) {
                                Text("${pendingRequests.size}", color = Color.White, fontSize = 9.sp)
                            }
                        }},
                        onClick = { showChatMenu = false; chatMenuScreen = "received" }
                    )
                    DropdownMenuItem(
                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                            Text("Blocked Users", color = chatTextPrimary(), fontSize = 14.sp)
                            if (blockedUsers.isNotEmpty()) Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF444444)), contentAlignment = Alignment.Center) {
                                Text("${blockedUsers.size}", color = Color.White, fontSize = 9.sp)
                            }
                        }},
                        onClick = { showChatMenu = false; chatMenuScreen = "blocked"; viewModel.loadBlockedUsers() }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Direct", "Group", "Community", "AI 🤖").forEachIndexed { i, label ->
                Column(Modifier.clickable { selectedTab = i }.padding(8.dp, 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = if (selectedTab == i) AccentRed else TextSec,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        if (i == 0 && pendingRequests.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.size(16.dp).clip(CircleShape).background(AccentRed), contentAlignment = Alignment.Center) {
                                Text("${pendingRequests.size}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
            if (selectedTab == 1) {
                IconButton(onClick = { viewModel.showCreateGroupDialog() }) {
                    Icon(Icons.Default.Add, null, tint = AccentRed, modifier = Modifier.size(22.dp))
                }
            } else if (selectedTab == 0) {
                IconButton(onClick = { viewModel.showNewChatDialog() }) {
                    Icon(Icons.Default.Add, null, tint = AccentRed, modifier = Modifier.size(22.dp))
                }
            }
        }
        Divider(color = chatDivider(), thickness = 0.5.dp)
        when (selectedTab) {
            2 -> CommunityChatScreen(viewModel = viewModel)
            3 -> AiCompanionChatTab(viewModel = viewModel)
            else -> {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentRed) }
                } else {
                    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                        // Pending requests section (Direct tab only)
                        if (selectedTab == 0 && pendingRequests.isNotEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().background(Color(0xFF1A0A0A)).padding(12.dp, 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(AccentRed))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Chat Requests (${pendingRequests.size})", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    pendingRequests.forEach { req ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(Modifier.size(36.dp).clip(CircleShape).background(avatarGradient(0)), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(req.fromName, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Chat karna chahta hai", color = chatTextSec(), fontSize = 12.sp)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF4CAF50)).clickable { viewModel.acceptChatRequest(req.requestId) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                    Text("Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A1A1A)).clickable { viewModel.rejectChatRequest(req.requestId) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                    Text("Reject", color = AccentRed, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Divider(color = chatDivider(), thickness = 0.5.dp)
                            }
                        }
                        // Sent requests section removed — visible in 3-dot menu only
                        if (filtered.isEmpty() && pendingRequests.isEmpty()) {
                            item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Koi chat nahi", color = chatTextSec(), fontSize = 14.sp) } }
                        } else {
                            itemsIndexed(filtered) { i, room ->
                                val isOnline = if (!room.isGroup) room.otherUserId?.let { viewModel.isUserOnline(it) } ?: false else false
                                ChatRoomRow(
                                    room = room, avatarIndex = i,
                                    onClick = { onRoomClick(room) },
                                    onLongPress = { menuRoom = room; showRoomMenu = true },
                                    isOnline = isOnline
                                )
                                Divider(color = chatDivider(), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    // Long press room menu
    if (showRoomMenu && menuRoom != null) {
        val room = menuRoom!!
        Dialog(onDismissRequest = { showRoomMenu = false; menuRoom = null }) {
            Box(Modifier.fillMaxSize().clickable { showRoomMenu = false; menuRoom = null }, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(chatCard())
                        .padding(bottom = 8.dp)
                        .clickable(enabled = false) {}
                ) {
                    // Room preview header
                    Row(Modifier.fillMaxWidth().background(chatCardAlt()).padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(avatarGradient(0)), contentAlignment = Alignment.Center) {
                            if (!room.pic.isNullOrEmpty()) {
                                AsyncImage(model = room.pic, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            } else {
                                Icon(if (room.isGroup) Icons.Default.Group else Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(24.dp))
                            }
                        }
                        Column {
                            Text(room.name, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (room.isGroup) "Group" else "Direct Chat", color = chatTextSec(), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Open
                    Row(Modifier.fillMaxWidth().clickable { showRoomMenu = false; menuRoom = null; onRoomClick(room) }.padding(16.dp, 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (room.isGroup) Icons.Default.Group else Icons.Default.Chat, null, tint = chatTextPrimary(), modifier = Modifier.size(20.dp))
                        Text(if (room.isGroup) "Open Group" else "Open Chat", color = chatTextPrimary(), fontSize = 14.sp)
                    }
                    Divider(color = chatDivider(), thickness = 0.5.dp)
                    // Leave group / Delete chat
                    if (room.isGroup) {
                        Row(Modifier.fillMaxWidth().clickable {
                            showRoomMenu = false
                            menuRoom = null
                            showLeaveConfirm = true
                        }.padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                            Text("Leave & Delete Group", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().clickable {
                            showRoomMenu = false
                            viewModel.deleteRoomLocally(room._id)
                            menuRoom = null
                        }.padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                            Text("Delete Chat", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Leave & delete group confirm
    if (showLeaveConfirm && menuRoom != null) {
        val room = menuRoom!!
        Dialog(onDismissRequest = { showLeaveConfirm = false; menuRoom = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = chatCard()), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🗑️ Delete Group?", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("\"${room.name}\" group se leave ho jaoge aur yeh list se delete ho jaayega.", color = chatTextSec(), fontSize = 14.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showLeaveConfirm = false; menuRoom = null }) {
                            Text("Cancel", color = chatTextSec())
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFFF6B6B))
                            .clickable {
                                showLeaveConfirm = false
                                menuRoom = null
                                viewModel.leaveGroupById(room._id)
                            }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showNewChat) NewChatDialog(
        users = users,
        groupResults = groupSearchResults,
        onSearchUsers = { viewModel.loadUsers(it) },
        onSearchGroups = { viewModel.searchGroups(it) },
        onDm = { viewModel.sendChatRequest(it) },
        onCreateGroup = { viewModel.showCreateGroupDialog() },
        onJoinGroup = { groupId -> viewModel.joinGroupDirect(groupId) },
        onInviteToGroup = { groupId -> /* open group invite flow */ },
        initialTab = if (selectedTab == 1) 1 else 0,
        onDismiss = { viewModel.hideNewChatDialog() }
    )
    if (showCreateGroup) CreateGroupDialog(users, { viewModel.loadUsers(it) }, { n, ids -> viewModel.createGroup(n, ids) }, { viewModel.hideCreateGroupDialog() })

    // Sent Requests Dialog
    if (chatMenuScreen == "sent") {
        Dialog(onDismissRequest = { chatMenuScreen = "" }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("📤 Sent Requests", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { chatMenuScreen = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (sentRequests.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Koi sent request nahi", color = chatTextSec(), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f, false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sentRequests) { req ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(chatCardAlt()).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(avatarGradient(1)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(req.toName, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Pending ⏳", color = chatTextSec(), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Received Requests Dialog
    if (chatMenuScreen == "received") {
        Dialog(onDismissRequest = { chatMenuScreen = "" }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("📥 Received Requests", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { chatMenuScreen = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (pendingRequests.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Koi received request nahi", color = chatTextSec(), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f, false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pendingRequests) { req ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(chatCardAlt()).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(avatarGradient(0)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(req.fromName, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Chat karna chahta hai", color = chatTextSec(), fontSize = 12.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF4CAF50))
                                            .clickable { viewModel.acceptChatRequest(req.requestId); if (pendingRequests.size == 1) chatMenuScreen = "" }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A1A1A))
                                            .clickable { viewModel.rejectChatRequest(req.requestId) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text("✕", color = AccentRed, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Blocked Users Dialog
    if (chatMenuScreen == "blocked") {
        Dialog(onDismissRequest = { chatMenuScreen = "" }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("🚫 Blocked Users", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { chatMenuScreen = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (blockedUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Koi blocked user nahi", color = chatTextSec(), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f, false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(blockedUsers) { user ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(chatCardAlt()).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // No profile pic for blocked users
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Block, null, tint = Color(0xFF666666), modifier = Modifier.size(22.dp))
                                    }
                                    Text(user.name, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A2A1A))
                                        .clickable { viewModel.unblockUserById(user.userId, user.roomId) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text("Unblock", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityChatScreen(viewModel: ChatViewModel) {
    val communityMessages by viewModel.communityMessages.collectAsState()
    val communityOnline by viewModel.communityOnline.collectAsState()
    val myId by viewModel.myIdFlow.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { viewModel.joinCommunity() }
    LaunchedEffect(communityMessages.size) {
        if (communityMessages.isNotEmpty()) listState.scrollToItem(communityMessages.size - 1)
    }

    // Context menu state
    var contextMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var replyTo by remember { mutableStateOf<ReplyRef?>(null) }
    var showEmojiPanel by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(chatBg()).navigationBarsPadding()) {
        // Header
        Row(Modifier.fillMaxWidth().background(chatCard()).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = AccentRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Community Chat", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(AccentRed).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if (communityOnline > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    Spacer(Modifier.width(4.dp))
                    Text("$communityOnline online", color = Color(0xFF4CAF50), fontSize = 11.sp)
                }
            }
        }
        Divider(color = chatDivider(), thickness = 0.5.dp)

        // Messages
        Box(Modifier.weight(1f).then(
            if (showEmojiPanel) Modifier.pointerInput(Unit) { detectTapGestures { showEmojiPanel = false } } else Modifier
        )) {
            if (communityMessages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Koi message nahi abhi", color = chatTextSec(), fontSize = 14.sp)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(communityMessages, key = { it._id }) { msg ->
                        MessageBubble(
                            msg = msg, myId = myId,
                            onLongClick = { contextMsg = msg },
                            onSwipeReply = { replyTo = ReplyRef(msgId = it._id, text = it.text, senderName = it.senderName) }
                        )
                    }
                }
            }
        }

        // Reply preview
        replyTo?.let { reply ->
            Row(Modifier.fillMaxWidth().background(chatCardAlt()).padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(28.dp).background(AccentRed))
                Spacer(Modifier.width(8.dp))
                Text(reply.text.take(50), color = chatTextSec(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { replyTo = null }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                }
            }
        }

        // Emoji panel
        if (showEmojiPanel) {
            EmojiPickerPanel(onEmojiSelected = { inputText += it }, onDismiss = { showEmojiPanel = false })
        }

        // Input row
        Row(Modifier.fillMaxWidth().background(chatCard()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("😊", fontSize = 20.sp, modifier = Modifier.clickable { showEmojiPanel = !showEmojiPanel }.padding(4.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).padding(14.dp, 10.dp)) {
                if (inputText.isEmpty()) Text("Message...", color = chatTextSec(), fontSize = 14.sp)
                BasicTextField(value = inputText, onValueChange = { inputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendCommunityMessage(inputText.trim(), replyTo)
                            inputText = ""; replyTo = null; showEmojiPanel = false
                        }
                    }),
                    maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            if (showEmojiPanel) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2A1A1A))
                    .clickable {
                        if (inputText.isNotEmpty()) {
                            val bi = java.text.BreakIterator.getCharacterInstance()
                            bi.setText(inputText); bi.last()
                            val s = bi.previous()
                            if (s != java.text.BreakIterator.DONE) inputText = inputText.substring(0, s)
                        }
                    }, contentAlignment = Alignment.Center) {
                    Text("⌫", fontSize = 18.sp, color = Color(0xFFFF6B6B))
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B)))),
                contentAlignment = Alignment.Center) {
                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendCommunityMessage(inputText.trim(), replyTo)
                        inputText = ""; replyTo = null; showEmojiPanel = false
                    }
                }) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // Context menu — same as p2p
    contextMsg?.let { msg ->
        val isMine = msg.senderId == myId
        Dialog(onDismissRequest = { contextMsg = null }) {
            Box(Modifier.fillMaxSize().clickable { contextMsg = null }, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(chatCard())
                        .padding(bottom = 16.dp)
                        .clickable(enabled = false) {}
                ) {
                    // Message preview
                    Box(Modifier.fillMaxWidth().background(chatCardAlt()).padding(16.dp, 12.dp)) {
                        Text(msg.text.take(80) + if (msg.text.length > 80) "..." else "", color = chatTextSec(), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Quick emoji reactions
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("❤️","😂","😮","😢","👍","🔥").forEach { emoji ->
                            Text(emoji, fontSize = 26.sp, modifier = Modifier.clickable {
                                viewModel.reactToCommunityMessage(msg._id, emoji)
                                contextMsg = null
                            }.padding(4.dp))
                        }
                    }
                    Divider(color = chatDivider(), thickness = 0.5.dp)
                    // Reply
                    Row(Modifier.fillMaxWidth().clickable {
                        replyTo = ReplyRef(msgId = msg._id, text = msg.text, senderName = msg.senderName)
                        contextMsg = null
                    }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Default.Reply, null, tint = chatTextPrimary(), modifier = Modifier.size(20.dp))
                        Text("Reply", color = chatTextPrimary(), fontSize = 14.sp)
                    }
                    Divider(color = chatDivider(), thickness = 0.5.dp)
                    // Copy
                    Row(Modifier.fillMaxWidth().clickable {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", msg.text))
                        viewModel.setToast("Copied ✓")
                        contextMsg = null
                    }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Default.ContentCopy, null, tint = chatTextPrimary(), modifier = Modifier.size(20.dp))
                        Text("Copy", color = chatTextPrimary(), fontSize = 14.sp)
                    }
                    // Delete (own messages only)
                    if (isMine) {
                        Divider(color = chatDivider(), thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth().clickable {
                            viewModel.deleteCommunityMessage(msg._id)
                            contextMsg = null
                        }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                            Text("Delete", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRoomRow(room: ChatRoom, avatarIndex: Int, onClick: () -> Unit, onLongPress: () -> Unit = {}, isOnline: Boolean = false) {
    Row(Modifier.fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongPress)
        .background(chatBg()).padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            if (!room.pic.isNullOrEmpty()) {
                AsyncImage(model = room.pic, contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(52.dp).clip(CircleShape).background(avatarGradient(avatarIndex)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(28.dp))
                }
            }
            // Online/offline dot
            Box(Modifier.size(13.dp).clip(CircleShape)
                .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFF666666))
                .align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(room.name, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(formatChatTime(room.lastTime ?: ""), color = chatTextSec(), fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(room.lastMessage ?: "Tap to chat", color = chatTextSec(), fontSize = 13.sp, maxLines = 1)
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
fun ChatWindowScreen(
    room: ChatRoom,
    viewModel: ChatViewModel,
    context: Context,
    onBack: () -> Unit,
    callViewModel: CallViewModel? = null,
    voiceChatViewModel: GroupVoiceChatViewModel? = null,
    onPickFile: (String, (android.net.Uri, String, String) -> Unit) -> Unit = { _, _ -> },
    onRequestScreenCapture: (() -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsState()
    val myId by viewModel.myIdFlow.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val pinnedMsg by viewModel.pinnedMsg.collectAsState()
    val contextMsg by viewModel.contextMsg.collectAsState()
    val showStarred by viewModel.showStarred.collectAsState()
    val showPinned by viewModel.showPinned.collectAsState()
    val starredMessages by viewModel.starredMessages.collectAsState()
    val showGroupInfo by viewModel.showGroupInfo.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val isBlockedByMe by viewModel.isBlockedByMe.collectAsState()
    val isBlockedByThem by viewModel.isBlockedByThem.collectAsState()
    val onlineUsers by viewModel.onlineUsers.collectAsState()
    val isPartnerOnline = if (!room.isGroup) room.otherUserId?.let { onlineUsers.contains(it) } ?: false else false
    var inputText by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showInviteLink by remember { mutableStateOf("") }
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardMsgId by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteMsgId by remember { mutableStateOf("") }
    var deleteMsgIsMine by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showDeleteChatsMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    val displayMessages = if (showSearchBar && searchQuery.isNotBlank())
        messages.filter { it.text.contains(searchQuery, ignoreCase = true) } else messages

    // Wallpaper background
    val chatWallpapers by viewModel.chatWallpapers.collectAsState()
    val wallpaperName = chatWallpapers[room._id] ?: viewModel.getChatWallpaper(room._id)
    val wallpaperBrush: Brush = when (wallpaperName) {
        "midnight_blue" -> Brush.linearGradient(listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E), Color(0xFF0D0D1A)))
        "deep_purple"   -> Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF3D1A6E), Color(0xFF1A0A2E)))
        "ocean"         -> Brush.linearGradient(listOf(Color(0xFF001A2E), Color(0xFF003D5C), Color(0xFF001A2E)))
        "forest"        -> Brush.linearGradient(listOf(Color(0xFF0A1A0A), Color(0xFF1A3A1A), Color(0xFF0A1A0A)))
        "sunset"        -> Brush.linearGradient(listOf(Color(0xFF2E0A0A), Color(0xFF5C1A00), Color(0xFF2E0A0A)))
        "rose_gold"     -> Brush.linearGradient(listOf(Color(0xFF2E1A1A), Color(0xFF5C2A2A), Color(0xFF2E1A1A)))
        "galaxy"        -> Brush.linearGradient(listOf(Color(0xFF0A0A1A), Color(0xFF1A0A3E), Color(0xFF2E0A2E)))
        "aurora"        -> Brush.linearGradient(listOf(Color(0xFF001A1A), Color(0xFF003D2E), Color(0xFF001A3D)))
        "lava"          -> Brush.linearGradient(listOf(Color(0xFF1A0000), Color(0xFF3D0A00), Color(0xFF1A0000)))
        "teal_dark"     -> Brush.linearGradient(listOf(Color(0xFF001A1A), Color(0xFF004D4D), Color(0xFF001A1A)))
        "indigo_night"  -> Brush.linearGradient(listOf(Color(0xFF0A0A2E), Color(0xFF1A1A5E), Color(0xFF0A0A2E)))
        "emerald"       -> Brush.linearGradient(listOf(Color(0xFF001A0A), Color(0xFF004D1A), Color(0xFF001A0A)))
        "crimson"       -> Brush.linearGradient(listOf(Color(0xFF1A0000), Color(0xFF4D0000), Color(0xFF1A0000)))
        "cosmic"        -> Brush.linearGradient(listOf(Color(0xFF0A001A), Color(0xFF1A003D), Color(0xFF2E001A)))
        "slate"         -> Brush.linearGradient(listOf(Color(0xFF0A0F14), Color(0xFF1A2A3A), Color(0xFF0A0F14)))
        else            -> Brush.linearGradient(listOf(chatBg(), chatBg()))
    }

    Column(Modifier.fillMaxSize().background(wallpaperBrush).imePadding()) {
        // Top accent bar
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(AccentRed, Color(0xFFFF6B6B)))))

        // Header
        Row(Modifier.fillMaxWidth().background(chatCard()).padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
            Box(Modifier.size(38.dp).clip(CircleShape).background(avatarGradient(0)).clickable {
                if (room.isGroup) viewModel.loadGroupInfo(room._id)
            }, contentAlignment = Alignment.Center) {
                if (!room.pic.isNullOrEmpty()) {
                    AsyncImage(model = room.pic, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Icon(if (room.isGroup) Icons.Default.Group else Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { if (room.isGroup) viewModel.loadGroupInfo(room._id) }) {
                Text(room.name, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    when {
                        isBlockedByMe -> "Blocked"
                        isBlockedByThem -> "Blocked you"
                        isTyping -> "typing..."
                        else -> if (room.isGroup) "${room.members?.size ?: 0} members" else if (isPartnerOnline) "online" else "offline"
                    },                    color = when {
                        isBlockedByMe || isBlockedByThem -> Color(0xFFFF6B6B)
                        isTyping -> AccentRed
                        !room.isGroup && !isPartnerOnline -> Color(0xFF666666)
                        else -> Color(0xFF4CAF50)
                    },
                    fontSize = 11.sp
                )
            }
            // Call buttons
            if (callViewModel != null && !isBlockedByMe && !isBlockedByThem) {
                IconButton(onClick = {
                    if (room.isGroup) {
                        // Group call — sirf owner start kare, admins join kar sakte hain
                        val isOwner = groupInfo?.isAdmin == true
                        val isAdmin = groupInfo?.room?.subAdmins?.any { it.userId == myId } == true
                        val canJoin = isOwner || isAdmin
                        if (canJoin) {
                            callViewModel.startCall(room._id, room.name, "audio")
                        } else {
                            viewModel.setToast("📞 Sirf Owner aur Admins call mein join kar sakte hain")
                        }
                    } else {
                        callViewModel.startCall(room._id, room.name, "audio")
                    }
                }) {
                    Icon(Icons.Default.Call, null, tint = chatTextSec(), modifier = Modifier.size(20.dp))
                }
                if (!room.isGroup) {
                    IconButton(onClick = { callViewModel.startCall(room._id, room.name, "video") }) {
                        Icon(Icons.Default.Videocam, null, tint = chatTextSec(), modifier = Modifier.size(20.dp))
                    }
                }
            }
            // Group voice chat button — owner/admin start kar sake, member join kar sake agar active ho
            if (room.isGroup && voiceChatViewModel != null) {
                IconButton(onClick = {
                    val isOwner = groupInfo?.isAdmin == true
                    val subAdmin = groupInfo?.room?.subAdmins?.firstOrNull { it.userId == myId }
                    val canStart = isOwner || subAdmin?.canStartVoiceChat == true
                    val vcActive = voiceChatViewModel.isActive.value
                    when {
                        canStart -> voiceChatViewModel.join(room._id, myId, "", "")
                        vcActive -> voiceChatViewModel.join(room._id, myId, "", "") // join existing
                        else -> viewModel.setToast("🎙️ Voice chat abhi active nahi hai. Sirf Owner/Admin start kar sakte hain")
                    }
                }) {
                    Icon(Icons.Default.Mic, null, tint = chatTextSec(), modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = chatTextSec()) }
            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false },
                modifier = Modifier.background(chatCard()), offset = androidx.compose.ui.unit.DpOffset(x = (-160).dp, y = 0.dp)) {
                val menuItems = buildList {
                    add("Search in Chat")
                    add("Starred Messages")
                    add("Pinned Messages")
                    if (room.isGroup) { add("Group Info"); add("Invite Link") }
                    if (!room.isGroup) { if (isBlockedByMe) add("Unblock User") else add("Block User") }
                    add("Delete Chats ⏱")
                    add("Clear Chat")
                }
                menuItems.forEach { item ->
                    DropdownMenuItem(text = { Text(item, color = if (item.contains("Block") || item == "Clear Chat") Color(0xFFFF6B6B) else if (item == "Delete Chats ⏱") Color(0xFFFFAA00) else TextPrimary, fontSize = 14.sp) },
                        onClick = {
                            showMoreMenu = false
                            when (item) {
                                "Search in Chat" -> { showSearchBar = !showSearchBar; if (!showSearchBar) searchQuery = "" }
                                "Starred Messages" -> viewModel.showStarredPanel()
                                "Pinned Messages" -> viewModel.showPinnedPanel()
                                "Group Info" -> viewModel.loadGroupInfo(room._id)
                                "Invite Link" -> viewModel.getGroupInviteLink(room._id) { link -> showInviteLink = link }
                                "Block User" -> viewModel.blockUser {}
                                "Unblock User" -> viewModel.unblockUser {}
                                "Delete Chats ⏱" -> showDeleteChatsMenu = true
                                "Clear Chat" -> viewModel.clearChat()
                            }
                        })
                }
            }
        }

        // Search bar
        if (showSearchBar) {
            Row(Modifier.fillMaxWidth().background(chatCardAlt()).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(chatSearchBg()).padding(14.dp, 8.dp)) {
                    if (searchQuery.isEmpty()) Text("Search in chat...", color = chatTextSec(), fontSize = 13.sp)
                    BasicTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Pinned message
        pinnedMsg?.let { msg ->
            Row(Modifier.fillMaxWidth().background(chatCardAlt()).padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(32.dp).background(AccentRed))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Pinned", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(msg.text.take(60), color = chatTextSec(), fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        // Blocked banner
        if (isBlockedByMe || isBlockedByThem) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF2A1A1A)).padding(12.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (isBlockedByMe) "Aapne is user ko block kiya hai. Unblock karne ke liye menu use karo."
                    else "Is user ne aapko block kiya hai.",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp
                )
            }
        }

        // Messages list — emoji panel open hone pe bahar tap se close
        Box(
            Modifier.weight(1f)
                .then(if (showEmojiPanel) Modifier.pointerInput(Unit) {
                    detectTapGestures { showEmojiPanel = false }
                } else Modifier)
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(displayMessages) { msg ->
                    MessageBubble(msg = msg, myId = myId, onLongClick = { viewModel.onMsgLongPress(msg) },
                        onSwipeReply = { swipedMsg -> viewModel.setReply(swipedMsg) })
                }
            }
        }

        // Reply preview
        replyTo?.let { reply ->
            Row(Modifier.fillMaxWidth().background(chatCardAlt()).padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(28.dp).background(AccentRed))
                Spacer(Modifier.width(8.dp))
                Text(reply.text.take(50), color = chatTextSec(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.cancelReply() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                }
            }
        }

        // Input row
        if (!isBlockedByMe && !isBlockedByThem) {
            var isVoiceRecording by remember { mutableStateOf(false) }
            Column {
                // Emoji panel — hide during recording
                if (showEmojiPanel && !isVoiceRecording) {
                    EmojiPickerPanel(
                        onEmojiSelected = { emoji -> inputText += emoji },
                        onDismiss = { showEmojiPanel = false }
                    )
                }
                // Attachment popup — floats above 📎 button in one column
                if (showAttachMenu && !isVoiceRecording) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.BottomStart,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(chatCardAlt())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(
                                Triple("🖼", "Photo", "image/*"),
                                Triple("🎥", "Video", "video/*"),
                                Triple("📄", "Document", "*/*"),
                                Triple("🎵", "Audio", "audio/*")
                            ).forEach { (emoji, label, mime) ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            showAttachMenu = false
                                            onPickFile(mime) { uri, mimeType, displayName ->
                                                // Actual mimeType use karo — document picker se bhi image/video/pdf sahi detect ho
                                                viewModel.sendFile(room._id, uri, mimeType, displayName)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(chatBg()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                    Text(label, color = chatTextPrimary(), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                // Single Row — VoiceRecordButton always present, others hide during recording
                Row(
                    Modifier.fillMaxWidth().background(chatCard()).padding(horizontal = 8.dp, vertical = 4.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isVoiceRecording) {
                        Text("📎", fontSize = 20.sp, modifier = Modifier.clickable { showAttachMenu = !showAttachMenu; showEmojiPanel = false }.padding(4.dp))
                        Text("😊", fontSize = 20.sp, modifier = Modifier.clickable { showEmojiPanel = !showEmojiPanel; showAttachMenu = false }.padding(4.dp))
                    }
                    VoiceRecordButton(
                        onVoiceSent = { file -> viewModel.sendVoiceFile(room._id, file) },
                        onRecordingStateChanged = { isVoiceRecording = it }
                    )
                    if (!isVoiceRecording) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).padding(14.dp, 10.dp)) {
                            if (inputText.isEmpty()) Text("Message...", color = chatTextSec(), fontSize = 14.sp)
                            BasicTextField(value = inputText, onValueChange = { inputText = it; viewModel.onTyping(room._id, true) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 14.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (inputText.isNotBlank()) { viewModel.sendMessage(room._id, inputText.trim()); inputText = ""; showEmojiPanel = false }
                                }),
                                maxLines = 4, modifier = Modifier.fillMaxWidth())
                        }
                        // ⌫ send ke LEFT mein — sirf emoji panel open hone pe
                        if (showEmojiPanel) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape)
                                    .background(Color(0xFF2A1A1A))
                                    .clickable {
                                        if (inputText.isNotEmpty()) {
                                            val breakIter = java.text.BreakIterator.getCharacterInstance()
                                            breakIter.setText(inputText)
                                            breakIter.last()
                                            val start = breakIter.previous()
                                            if (start != java.text.BreakIterator.DONE) inputText = inputText.substring(0, start)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(text = "⌫", fontSize = 20.sp, color = Color(0xFFFF6B6B)) }
                        }
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B)))),
                            contentAlignment = Alignment.Center) {
                            IconButton(onClick = { if (inputText.isNotBlank()) { viewModel.sendMessage(room._id, inputText.trim()); inputText = ""; showEmojiPanel = false } }) {
                                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().background(chatCard()).padding(16.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                Text("Message nahi bhej sakte", color = chatTextSec(), fontSize = 13.sp)
            }
        }
    }

    // Context menu — WhatsApp style bottom sheet
    contextMsg?.let { msg ->
        val isImage = !msg.fileUrl.isNullOrEmpty() && msg.fileType?.startsWith("image") == true
        val fullUrl = if (!msg.fileUrl.isNullOrEmpty()) {
            if (msg.fileUrl!!.startsWith("http")) msg.fileUrl!! else "https://api.picrypto.in${msg.fileUrl}"
        } else ""
        Dialog(onDismissRequest = { viewModel.dismissContext() }) {
            Box(
                Modifier.fillMaxSize().clickable { viewModel.dismissContext() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(chatCard())
                        .padding(bottom = 16.dp)
                        .clickable(enabled = false) { } // prevent dismiss when tapping menu itself
                ) {
                    // Preview of message
                    Box(Modifier.fillMaxWidth().background(chatCardAlt()).padding(16.dp, 12.dp)) {
                        when {
                            isImage -> AsyncImage(
                                model = fullUrl, contentDescription = null,
                                modifier = Modifier.height(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            !msg.fileUrl.isNullOrEmpty() && msg.fileType?.startsWith("audio") == true ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Mic, null, tint = AccentRed, modifier = Modifier.size(28.dp))
                                    Text("🎤 Voice Message", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            !msg.fileUrl.isNullOrEmpty() && msg.fileType?.startsWith("video") == true ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Videocam, null, tint = AccentRed, modifier = Modifier.size(28.dp))
                                    Text("🎥 Video", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            !msg.fileUrl.isNullOrEmpty() ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(28.dp))
                                    Text(msg.fileName ?: "File", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            else -> Text(
                                msg.text.take(80) + if (msg.text.length > 80) "..." else "",
                                color = chatTextSec(), fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Quick emoji reactions row
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("❤️", "😂", "😮", "😢", "👍", "🙏", "🔥").forEach { emoji ->
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(chatSearchBg())
                                    .clickable { viewModel.reactToMessage(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                    Divider(color = chatDivider(), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    // Action items
                    val hasFile = !msg.fileUrl.isNullOrEmpty()
                    // Live starred state from messages list (not stale contextMsg)
                    val liveMsg = messages.firstOrNull { it._id == msg._id }
                    val isStarred = liveMsg?.starred ?: msg.starred
                    val actions = buildList {
                        add(Triple("Reply", Icons.Default.Reply, false))
                        add(Triple("Forward", Icons.Default.Forward, false))
                        add(Triple(if (isStarred) "Unstar ★" else "Star ⭐", Icons.Default.Star, false))
                        add(Triple("Pin 📌", Icons.Default.PushPin, false))
                        if (msg.text.isNotBlank() && !hasFile) add(Triple("Copy", Icons.Default.ContentCopy, false))
                        // Edit — sirf apne text messages pe
                        if (msg.senderId == myId && msg.text.isNotBlank() && !hasFile) add(Triple("Edit ✏️", Icons.Default.Edit, false))
                        if (hasFile) {
                            add(Triple("Save / Open", Icons.Default.Download, false))
                            add(Triple("Share", Icons.Default.Share, false))
                        }
                        add(Triple("Delete", Icons.Default.Delete, true))
                    }
                    actions.forEach { (label, icon, isDanger) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    when (label) {
                                        "Reply" -> viewModel.setReplyFromContext()
                                        "Forward" -> { showForwardDialog = true; forwardMsgId = msg._id; viewModel.dismissContext() }
                                        "Edit ✏️" -> { editText = msg.text; showEditDialog = true }
                                        "Star ⭐" -> viewModel.starMessage()
                                        "Unstar ★" -> viewModel.starMessage()
                                        "Pin 📌" -> viewModel.pinMessage()
                                        "Copy" -> {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("msg", msg.text))
                                            viewModel.dismissContext()
                                        }
                                        "Save / Open" -> {
                                            // Direct download to phone using DownloadManager
                                            try {
                                                val fileName = msg.fileName ?: fullUrl.substringAfterLast('/').ifBlank { "file_${System.currentTimeMillis()}" }
                                                val request = android.app.DownloadManager.Request(android.net.Uri.parse(fullUrl)).apply {
                                                    setTitle(fileName)
                                                    setDescription("YT-Booster se download ho raha hai...")
                                                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                    when {
                                                        msg.fileType?.startsWith("image") == true ->
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "YTBooster/$fileName")
                                                        msg.fileType?.startsWith("video") == true ->
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "YTBooster/$fileName")
                                                        msg.fileType?.startsWith("audio") == true ->
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "YTBooster/$fileName")
                                                        else ->
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "YTBooster/$fileName")
                                                    }
                                                    setAllowedOverMetered(true)
                                                    setAllowedOverRoaming(true)
                                                }
                                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                dm.enqueue(request)
                                                viewModel.dismissContext()
                                                // Show toast via snackbar (handled by ChatScreen)
                                            } catch (e: Exception) {
                                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl))
                                                context.startActivity(Intent.createChooser(intent, "Open with"))
                                            }
                                            viewModel.dismissContext()
                                        }
                                        "Share" -> {
                                            val shareText = if (fullUrl.isNotEmpty()) fullUrl else msg.text
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = if (isImage) "image/*" else "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share"))
                                            viewModel.dismissContext()
                                        }                                        "Delete" -> {
                                            deleteMsgId = msg._id
                                            deleteMsgIsMine = msg.senderId == myId
                                            viewModel.dismissContext()
                                            showDeleteDialog = true
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(icon, null, tint = if (isDanger) AccentRed else TextPrimary, modifier = Modifier.size(22.dp))
                            Text(label, color = if (isDanger) AccentRed else TextPrimary, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    // Starred messages panel
    if (showStarred) {
        Dialog(onDismissRequest = { viewModel.hideStarredPanel() }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐ Starred Messages", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { viewModel.hideStarredPanel() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (starredMessages.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⭐", fontSize = 36.sp)
                                Text("Koi starred message nahi", color = chatTextSec(), fontSize = 14.sp)
                            }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f, false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(starredMessages) { m ->
                                val mUrl = if (!m.fileUrl.isNullOrEmpty()) {
                                    if (m.fileUrl!!.startsWith("http")) m.fileUrl!! else "https://api.picrypto.in${m.fileUrl}"
                                } else ""
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(chatCardAlt())
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Content
                                    Column(Modifier.weight(1f)) {
                                        Text(m.senderName, color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(3.dp))
                                        when {
                                            !m.fileUrl.isNullOrEmpty() && m.fileType?.startsWith("image") == true ->
                                                AsyncImage(model = mUrl, contentDescription = null,
                                                    modifier = Modifier.height(80.dp).clip(RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Crop)
                                            !m.fileUrl.isNullOrEmpty() && m.fileType?.startsWith("audio") == true ->
                                                Text("🎤 Voice Message", color = chatTextPrimary(), fontSize = 13.sp)
                                            !m.fileUrl.isNullOrEmpty() && m.fileType?.startsWith("video") == true ->
                                                Text("🎥 Video", color = chatTextPrimary(), fontSize = 13.sp)
                                            !m.fileUrl.isNullOrEmpty() ->
                                                Text("📄 ${m.fileName ?: "File"}", color = chatTextPrimary(), fontSize = 13.sp)
                                            else ->
                                                Text(m.text, color = chatTextPrimary(), fontSize = 13.sp, maxLines = 3)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(formatChatTime(m.createdAt), color = chatTextSec(), fontSize = 10.sp)
                                    }
                                    // Unstar button
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape)
                                            .background(Color(0xFF2A2A1A))
                                            .clickable {
                                                viewModel.starMessageById(m._id)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⭐", fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pinned message panel
    if (showPinned) {
        Dialog(onDismissRequest = { viewModel.hidePinnedPanel() }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("📌 Pinned Message", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    pinnedMsg?.let { Text(it.text, color = chatTextPrimary(), fontSize = 13.sp) } ?: Text("Koi pinned message nahi", color = chatTextSec(), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.hidePinnedPanel() }, modifier = Modifier.align(Alignment.End)) { Text("Close", color = AccentRed) }
                }
            }
        }
    }

    // Invite link dialog
    if (showInviteLink.isNotEmpty()) {
        Dialog(onDismissRequest = { showInviteLink = "" }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔗 Group Invite Link", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(chatCardAlt()).padding(12.dp)) {
                        Text(showInviteLink, color = Color(0xFF29B6F6), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("link", showInviteLink))
                            showInviteLink = ""
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), modifier = Modifier.weight(1f)) {
                            Text("Copy", color = Color.White)
                        }
                        Button(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, showInviteLink) }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Group Link"))
                            showInviteLink = ""
                        }, colors = ButtonDefaults.buttonColors(containerColor = CardAlt), modifier = Modifier.weight(1f)) {
                            Text("Share", color = chatTextPrimary())
                        }
                    }
                }
            }
        }
    }

    // Forward dialog
    if (showForwardDialog) {
        val allRooms by viewModel.rooms.collectAsState()
        Dialog(onDismissRequest = { showForwardDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("↪ Forward to...", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f, false)) {
                        items(allRooms.filter { it._id != room._id }) { r ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    viewModel.forwardMessage(forwardMsgId, listOf(r._id))
                                    showForwardDialog = false
                                }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(avatarGradient(allRooms.indexOf(r))),
                                    contentAlignment = Alignment.Center) {
                                    Icon(if (r.isGroup) Icons.Default.Group else Icons.Default.Person, null,
                                        tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                                }
                                Text(r.name, color = chatTextPrimary(), fontSize = 14.sp)
                            }
                        }
                    }
                    TextButton(onClick = { showForwardDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = chatTextSec())
                    }
                }
            }
        }
    }

    // Edit message dialog
    if (showEditDialog) {        Dialog(onDismissRequest = { showEditDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✏️ Edit Message", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(chatCardAlt()).padding(12.dp)) {
                        BasicTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 14.sp),
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showEditDialog = false }) { Text("Cancel", color = chatTextSec()) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(Brush.horizontalGradient(listOf(AccentRed, Color(0xFFFF6B6B))))
                                .clickable {
                                    if (editText.isNotBlank()) {
                                        viewModel.editMessage(editText.trim())
                                        showEditDialog = false
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                }
            }
        }
    }

    // Delete message dialog — Delete for Everyone / Delete for Me
    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🗑️ Delete Message", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    // Delete for Everyone — sirf apne messages pe
                    if (deleteMsgIsMine) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A1A1A))
                                .clickable {
                                    viewModel.deleteMessageById(deleteMsgId, forEveryone = true)
                                    showDeleteDialog = false
                                }
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("Delete for Everyone", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Sabke liye delete ho jaayega", color = TextSec, fontSize = 12.sp)
                            }
                        }
                    }
                    // Delete for Me — hamesha available
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(CardAlt)
                            .clickable {
                                viewModel.deleteMessageById(deleteMsgId, forEveryone = false)
                                showDeleteDialog = false
                            }
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Delete for Me", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Sirf aapke liye delete hoga", color = TextSec, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { showDeleteDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = TextSec)
                    }
                }
            }
        }
    }

    // Group voice chat — full screen overlay
    if (voiceChatViewModel != null) {
        val vcActive by voiceChatViewModel.isActive.collectAsState()
        if (vcActive) {
            GroupVoiceChatScreen(
                roomName = room.name,
                viewModel = voiceChatViewModel,
                onLeave = { /* stays in chat */ },
                onRequestScreenCapture = onRequestScreenCapture
            )
            return
        }
    }

    // Group profile — full screen (Telegram style)
    if (showGroupInfo && groupInfo != null) {
        GroupProfileScreen(
            groupInfo = groupInfo!!,
            viewModel = viewModel,
            roomId = room._id,
            myId = myId,
            onBack = { viewModel.hideGroupInfo() }
        )
        return
    }

    // Delete Chats (Disappearing Messages) Dialog — Snapchat style
    if (showDeleteChatsMenu) {
        Dialog(onDismissRequest = { showDeleteChatsMenu = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("⏱ Delete Chats", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Messages automatically delete ho jayenge", color = chatTextSec(), fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    listOf(
                        Triple("After viewing", 1, "Dekhe ke baad delete"),
                        Triple("After 24 hours", 86400, "1 din baad delete"),
                        Triple("After 7 days", 604800, "7 din baad delete"),
                        Triple("Off", 0, "Auto-delete band karo")
                    ).forEach { (label, seconds, desc) ->
                        val currentSecs = room.disappearingSeconds
                        val isSelected = currentSecs == seconds
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentRed.copy(0.15f) else Color.Transparent)
                                .clickable {
                                    viewModel.setDisappearing(room._id, seconds)
                                    showDeleteChatsMenu = false
                                }
                                .padding(12.dp, 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape)
                                .background(if (isSelected) AccentRed else Color(0xFF333333)),
                                contentAlignment = Alignment.Center) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Column {
                                Text(label, color = if (isSelected) AccentRed else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                Text(desc, color = chatTextSec(), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showDeleteChatsMenu = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = chatTextSec())
                    }
                }
            }
        }
    }
}

@Composable
fun GroupInfoPanel(groupInfo: GroupInfoResponse, viewModel: ChatViewModel, roomId: String, myId: String) {
    var selectedMember by remember { mutableStateOf<GroupMember?>(null) }
    var showSubAdminPerms by remember { mutableStateOf(false) }
    var subAdminTarget by remember { mutableStateOf<GroupMember?>(null) }

    // SubAdmin permissions state
    var permDeleteMsg by remember { mutableStateOf(true) }
    var permBanMembers by remember { mutableStateOf(false) }
    var permInviteMembers by remember { mutableStateOf(true) }
    var permPinMessages by remember { mutableStateOf(false) }
    var permChangeInfo by remember { mutableStateOf(false) }

    val subAdmins = groupInfo.room.subAdmins ?: emptyList()

    fun memberRole(member: GroupMember): String {
        val isAdmin = groupInfo.room.members?.firstOrNull() == member._id ||
            groupInfo.isAdmin && member._id == myId
        return when {
            member._id == (groupInfo.room.members?.firstOrNull() ?: "") -> "Owner"
            subAdmins.any { it.userId == member._id } -> "Sub-Admin"
            else -> "Member"
        }
    }

    fun roleColor(role: String) = when (role) {
        "Owner" -> Color(0xFFFFD700)
        "Sub-Admin" -> Color(0xFF29B6F6)
        else -> TextSec
    }

    Dialog(onDismissRequest = { viewModel.hideGroupInfo() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("👥 Group Info", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { viewModel.hideGroupInfo() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(groupInfo.room.name, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (!groupInfo.room.description.isNullOrEmpty()) {
                    Text(groupInfo.room.description, color = chatTextSec(), fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))

                // Members list
                Text("Members (${groupInfo.members.size})", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(groupInfo.members) { member ->
                        val role = when {
                            subAdmins.any { it.userId == member._id } -> "Sub-Admin"
                            groupInfo.isAdmin && member._id == myId -> "Owner"
                            else -> "Member"
                        }
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = groupInfo.isAdmin && member._id != myId) {
                                    selectedMember = member
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(avatarGradient(groupInfo.members.indexOf(member))), contentAlignment = Alignment.Center) {
                                if (member.profilePic.isNotEmpty()) {
                                    AsyncImage(model = member.profilePic, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } else {
                                    Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(member.channelName, color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(role, color = roleColor(role), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            if (groupInfo.isAdmin && member._id != myId) {
                                Icon(Icons.Default.MoreVert, null, tint = chatTextSec(), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Admin actions
                if (groupInfo.isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Divider(color = chatDivider())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.getGroupInviteLink(roomId) {} },
                            colors = ButtonDefaults.buttonColors(containerColor = CardAlt),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Link, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Invite Link", color = chatTextPrimary(), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.hideGroupInfo() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = chatTextSec())
                }
            }
        }
    }

    // Member action sheet (Telegram style)
    selectedMember?.let { member ->
        val isSubAdmin = subAdmins.any { it.userId == member._id }
        Dialog(onDismissRequest = { selectedMember = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Member header
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(avatarGradient(0)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(member.channelName, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (isSubAdmin) "Sub-Admin" else "Member", color = if (isSubAdmin) Color(0xFF29B6F6) else TextSec, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = chatDivider())
                    Spacer(Modifier.height(8.dp))

                    // Promote to SubAdmin / Edit permissions
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A2A))
                            .clickable {
                                subAdminTarget = member
                                // Load existing perms if already subadmin
                                val existing = subAdmins.firstOrNull { it.userId == member._id }
                                permDeleteMsg = existing?.canDeleteMessages ?: true
                                permBanMembers = existing?.canBanMembers ?: false
                                permInviteMembers = existing?.canInviteMembers ?: true
                                permPinMessages = existing?.canPinMessages ?: false
                                permChangeInfo = existing?.canChangeGroupInfo ?: false
                                showSubAdminPerms = true
                                selectedMember = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(22.dp))
                        Column {
                            Text(if (isSubAdmin) "Edit Sub-Admin Rights" else "Make Sub-Admin", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Telegram style permissions set karo", color = chatTextSec(), fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Remove SubAdmin role
                    if (isSubAdmin) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A1A1A))
                                .clickable {
                                    viewModel.manageSubAdmin(roomId, member._id, "remove")
                                    selectedMember = null
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.RemoveModerator, null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                            Text("Remove Sub-Admin", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // Remove from group
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A1A1A))
                            .clickable {
                                viewModel.removeMemberFromGroup(roomId, member._id)
                                selectedMember = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PersonRemove, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                        Text("Remove from Group", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { selectedMember = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = chatTextSec())
                    }
                }
            }
        }
    }

    // SubAdmin permissions dialog (Telegram style)
    if (showSubAdminPerms && subAdminTarget != null) {
        Dialog(onDismissRequest = { showSubAdminPerms = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("⭐ Sub-Admin Rights", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subAdminTarget!!.channelName, color = Color(0xFF29B6F6), fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))

                    // Permission toggles
                    listOf(
                        Triple("Delete Messages", permDeleteMsg, { v: Boolean -> permDeleteMsg = v }),
                        Triple("Ban Members", permBanMembers, { v: Boolean -> permBanMembers = v }),
                        Triple("Invite Members", permInviteMembers, { v: Boolean -> permInviteMembers = v }),
                        Triple("Pin Messages", permPinMessages, { v: Boolean -> permPinMessages = v }),
                        Triple("Change Group Info", permChangeInfo, { v: Boolean -> permChangeInfo = v })
                    ).forEach { (label, value, onChange) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = chatTextPrimary(), fontSize = 14.sp)
                            Switch(
                                checked = value,
                                onCheckedChange = onChange,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRed, uncheckedTrackColor = Color(0xFF333333))
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showSubAdminPerms = false }) { Text("Cancel", color = chatTextSec()) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.manageSubAdmin(
                                    roomId, subAdminTarget!!._id, "add",
                                    mapOf(
                                        "canDeleteMessages" to permDeleteMsg,
                                        "canBanMembers" to permBanMembers,
                                        "canInviteMembers" to permInviteMembers,
                                        "canPinMessages" to permPinMessages,
                                        "canChangeGroupInfo" to permChangeInfo
                                    )
                                )
                                showSubAdminPerms = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) { Text("Save", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage, myId: String, onLongClick: () -> Unit, onSwipeReply: ((ChatMessage) -> Unit)? = null) {
    val isMine = myId.isNotEmpty() && msg.senderId == myId
    var showEmojiPicker by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 400f),
        label = "swipe"
    )

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reply arrow indicator for received messages (left side, shows when swiping right)
            if (!isMine && offsetX > 20f) {
                Icon(
                    Icons.Default.Reply, null,
                    tint = AccentRed.copy(alpha = (offsetX / 60f).coerceIn(0f, 1f)),
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
            }

            Column(
                Modifier
                    .widthIn(max = 270.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(animatedOffset.toInt(), 0) }
                    .clip(RoundedCornerShape(18.dp, 18.dp, if (isMine) 4.dp else 18.dp, if (isMine) 18.dp else 4.dp))
                    .background(if (isMine) Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B))) else Brush.linearGradient(listOf(CardDark, CardAlt)))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onLongClick() },
                            onTap = { /* single tap — handled per content type */ }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = if (isMine) -60f else 60f
                                if ((!isMine && offsetX > threshold) || (isMine && offsetX < threshold)) {
                                    onSwipeReply?.invoke(msg)
                                }
                                offsetX = 0f
                            },
                            onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                change.consume()
                                val newOffset = offsetX + dragAmount
                                offsetX = if (isMine) {
                                    newOffset.coerceIn(-80f, 0f)
                                } else {
                                    newOffset.coerceIn(0f, 80f)
                                }
                            }
                        )
                    }
                    .padding(12.dp, 8.dp)
            ) {
                if (!isMine) {
                    Text(msg.senderName, color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                }
                msg.replyTo?.let { reply ->
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.2f)).padding(8.dp)) {
                        Text(reply.text.take(50), color = Color.White.copy(0.7f), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // File/voice message
                if (!msg.fileUrl.isNullOrEmpty()) {
                    val ctx = LocalContext.current
                    val fullUrl = if (msg.fileUrl!!.startsWith("http")) msg.fileUrl!! else "https://api.picrypto.in${msg.fileUrl}"
                    when {
                        msg.fileType?.startsWith("image") == true -> {
                            // Image — show thumbnail, tap to open full-screen viewer in-app
                            var showFullImage by remember { mutableStateOf(false) }
                            AsyncImage(
                                model = fullUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { showFullImage = true },
                                            onLongPress = { onLongClick() }
                                        )
                                    },
                                contentScale = ContentScale.FillWidth
                            )
                            if (showFullImage) {
                                Dialog(onDismissRequest = { showFullImage = false }) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(0.95f))
                                            .clickable { showFullImage = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = fullUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        // Close button
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(12.dp)
                                                .size(36.dp).clip(CircleShape)
                                                .background(Color.Black.copy(0.6f))
                                                .clickable { showFullImage = false },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                        // Download button
                                        Box(
                                            Modifier.align(Alignment.BottomEnd).padding(16.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(AccentRed)
                                                .clickable {
                                                    try {
                                                        val fileName = msg.fileName ?: "image_${System.currentTimeMillis()}.jpg"
                                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(fullUrl)).apply {
                                                            setTitle(fileName)
                                                            setDescription("Saving image...")
                                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "YTBooster/$fileName")
                                                            setAllowedOverMetered(true)
                                                        }
                                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                        dm.enqueue(request)
                                                        showFullImage = false
                                                    } catch (e: Exception) {
                                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl))
                                                        ctx.startActivity(intent)
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                Text("Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        msg.fileType?.startsWith("video") == true -> {
                            val sizeLabel = msg.fileSize?.let {
                                if (it > 50 * 1024 * 1024) "📥 ${it / (1024 * 1024)}MB - Tap to download" else "▶ Tap to play"
                            } ?: "▶ Tap to play"
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.3f))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                val fileSizeBytes = msg.fileSize ?: 0L
                                                if (fileSizeBytes > 50 * 1024 * 1024) {
                                                    // >50MB — download via DownloadManager
                                                    try {
                                                        val fileName = msg.fileName ?: "video_${System.currentTimeMillis()}.mp4"
                                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(fullUrl)).apply {
                                                            setTitle(fileName)
                                                            setDescription("Downloading...")
                                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "YTBooster/$fileName")
                                                            setAllowedOverMetered(true)
                                                        }
                                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                        dm.enqueue(request)
                                                    } catch (e: Exception) { }
                                                } else {
                                                    // <=50MB — stream directly
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(android.net.Uri.parse(fullUrl), "video/*")
                                                    }
                                                    try { ctx.startActivity(intent) }
                                                    catch (e: Exception) { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl))) }
                                                }
                                            },
                                            onLongPress = { onLongClick() }
                                        )
                                    }.padding(10.dp, 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                Column {
                                    Text("🎥 Video", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(sizeLabel, color = Color.White.copy(0.6f), fontSize = 10.sp)
                                }
                            }
                        }
                        msg.fileType?.startsWith("audio") == true -> {
                            // Voice message — WhatsApp style
                            var isPlaying by remember { mutableStateOf(false) }
                            var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(0.2f))
                                    .pointerInput(Unit) {
                                        detectTapGestures(onLongPress = { onLongClick() })
                                    }.padding(10.dp, 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(if (isPlaying) AccentRed else Color.White.copy(0.2f))
                                        .clickable {
                                            if (isPlaying) {
                                                mediaPlayer?.pause()
                                                isPlaying = false
                                            } else {
                                                try {
                                                    mediaPlayer?.release()
                                                    val mp = android.media.MediaPlayer().apply {
                                                        setDataSource(fullUrl)
                                                        setOnPreparedListener { start(); }
                                                        setOnCompletionListener { isPlaying = false }
                                                        prepareAsync()
                                                    }
                                                    mediaPlayer = mp
                                                    isPlaying = true
                                                } catch (e: Exception) { isPlaying = false }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null, tint = Color.White, modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text("🎤 Voice", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(if (isPlaying) "Playing..." else "Tap to play", color = Color.White.copy(0.6f), fontSize = 10.sp)
                                }
                            }
                        }
                        else -> {
                            // Document / other file — WhatsApp style with extension badge
                            val fileName = msg.fileName ?: "file"
                            val ext = fileName.substringAfterLast('.', "").uppercase().take(4)
                            val extColor = when (ext.lowercase()) {
                                "pdf" -> Color(0xFFE53935)
                                "doc", "docx" -> Color(0xFF1565C0)
                                "xls", "xlsx" -> Color(0xFF2E7D32)
                                "ppt", "pptx" -> Color(0xFFE65100)
                                "zip", "rar" -> Color(0xFF6A1B9A)
                                "jpg", "jpeg", "png", "gif", "webp" -> Color(0xFF00838F)
                                "mp4", "mkv", "mov", "avi" -> Color(0xFF37474F)
                                "mp3", "m4a", "ogg", "wav" -> Color(0xFF4527A0)
                                "txt" -> Color(0xFF546E7A)
                                else -> Color(0xFF29B6F6)
                            }
                            val docSizeLabel = msg.fileSize?.let {
                                if (it > 50 * 1024 * 1024) "${it / (1024 * 1024)} MB" else "${it / 1024} KB"
                            } ?: ""
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.2f))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                val fileSizeBytes = msg.fileSize ?: 0L
                                                if (fileSizeBytes > 50 * 1024 * 1024) {
                                                    try {
                                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(fullUrl)).apply {
                                                            setTitle(fileName)
                                                            setDescription("Downloading...")
                                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "YTBooster/$fileName")
                                                            setAllowedOverMetered(true)
                                                        }
                                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                        dm.enqueue(request)
                                                    } catch (e: Exception) { }
                                                } else {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl))
                                                        ctx.startActivity(Intent.createChooser(intent, "Open with"))
                                                    } catch (e: Exception) { }
                                                }
                                            },
                                            onLongPress = { onLongClick() }
                                        )
                                    }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // File type badge
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(extColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (ext.isNotEmpty()) ext else "FILE",
                                        color = Color.White,
                                        fontSize = if (ext.length <= 3) 11.sp else 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(Modifier.widthIn(max = 150.dp)) {
                                    Text(fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                    if (docSizeLabel.isNotEmpty()) {
                                        Text(docSizeLabel, color = Color.White.copy(0.6f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(msg.text, color = Color.White, fontSize = 14.sp)
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.wrapContentWidth()) {
                    if (msg.starred) {
                        Text("⭐", fontSize = 10.sp)
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(formatChatTime(msg.createdAt), color = Color.White.copy(0.6f), fontSize = 10.sp)
                    if (msg.edited) {
                        Spacer(Modifier.width(3.dp))
                        Text("edited", color = Color.White.copy(0.45f), fontSize = 9.sp)
                    }
                    if (isMine && msg.read) {
                        Spacer(Modifier.width(4.dp))
                        Text("✓✓", color = Color(0xFF29B6F6), fontSize = 10.sp)
                    }
                }
            }

            // Reply arrow for sent messages (right side, shows when swiping left)
            if (isMine && offsetX < -20f) {
                Icon(
                    Icons.Default.Reply, null,
                    tint = AccentRed.copy(alpha = ((-offsetX) / 60f).coerceIn(0f, 1f)),
                    modifier = Modifier.size(20.dp).graphicsLayer(scaleX = -1f).padding(start = 4.dp)
                )
            }
        }

        // Reactions display — WhatsApp style, message bubble ke neeche
        if (msg.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(if (isMine) Alignment.End else Alignment.Start)
                    .offset(y = (-6).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(chatCard())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                msg.reactions.entries.take(6).forEach { (emoji, users) ->
                    val count = users.size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(emoji, fontSize = 14.sp)
                        if (count > 1) Text("$count", fontSize = 11.sp, color = Color(0xFFBBBBBB))
                    }
                }
            }
        }
    }

    // Emoji picker popup
    if (showEmojiPicker) {
        Dialog(onDismissRequest = { showEmojiPicker = false }) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(chatCard()).padding(12.dp)) {
                Column {
                    Text("React karo", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("❤️", "😂", "😮", "😢", "👍", "🙏").forEach { emoji ->
                            Text(
                                emoji, fontSize = 28.sp,
                                modifier = Modifier.clickable { showEmojiPicker = false; onLongClick() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewChatDialog(
    users: List<ChatUser>,
    groupResults: List<com.ytsubexchange.data.GroupSearchResult>,
    onSearchUsers: (String) -> Unit,
    onSearchGroups: (String) -> Unit,
    onDm: (ChatUser) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: (String) -> Unit,
    onInviteToGroup: (String) -> Unit,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedGroup by remember { mutableStateOf<com.ytsubexchange.data.GroupSearchResult?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(if (selectedTab == 0) "New Chat" else "Groups", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))

                // Tabs
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(chatSearchBg())) {
                    listOf("👤 Users", "👥 Groups").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedTab == i) AccentRed else Color.Transparent)
                                .clickable {
                                    selectedTab = i; query = ""
                                    if (i == 0) onSearchUsers("") else onSearchGroups("")
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selectedTab == i) Color.White else TextSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // ── Users Tab ─────────────────────────────
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).padding(14.dp, 10.dp)) {
                        if (query.isEmpty()) Text("User search karo...", color = chatTextSec(), fontSize = 13.sp)
                        BasicTextField(value = query, onValueChange = { query = it; onSearchUsers(it) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 13.sp),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f, false)) {
                        if (users.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    Text(if (query.isEmpty()) "Koi user nahi mila" else "\"$query\" nahi mila", color = chatTextSec(), fontSize = 13.sp)
                                }
                            }
                        }
                        itemsIndexed(users) { i, user ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onDm(user); onDismiss() }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(avatarGradient(i)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(user.channelName, color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Tap to send chat request", color = chatTextSec(), fontSize = 11.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = chatTextSec(), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    // ── Groups Tab ────────────────────────────
                    // Create Group button
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(AccentRed.copy(0.15f), Color(0xFFFF6B6B).copy(0.1f))))
                            .clickable { onCreateGroup(); onDismiss() }
                            .padding(14.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(AccentRed), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Create Group", color = AccentRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Naya group banao, members add karo", color = chatTextSec(), fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                    Divider(color = chatDivider())
                    Spacer(Modifier.height(8.dp))

                    // Group search
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).padding(14.dp, 10.dp)) {
                        if (query.isEmpty()) Text("Group search karo...", color = chatTextSec(), fontSize = 13.sp)
                        BasicTextField(value = query, onValueChange = { query = it; onSearchGroups(it) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 13.sp),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(Modifier.weight(1f, false)) {
                        if (query.length < 2) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    Text("2+ characters type karo group dhundne ke liye", color = chatTextSec(), fontSize = 12.sp)
                                }
                            }
                        } else {
                            if (groupResults.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                        Text("\"$query\" naam ka koi group nahi mila", color = chatTextSec(), fontSize = 13.sp)
                                    }
                                }
                            }
                            itemsIndexed(groupResults) { i, group ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedGroup = group }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(Modifier.size(42.dp).clip(CircleShape).background(avatarGradient(i)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Group, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${group.memberCount} members", color = chatTextSec(), fontSize = 11.sp)
                                    }
                                    if (group.isMember) {
                                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A3A1A)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("Joined", color = Color(0xFF4CAF50), fontSize = 10.sp)
                                        }
                                    } else {
                                        Icon(Icons.Default.ChevronRight, null, tint = chatTextSec(), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = chatTextSec())
                }
            }
        }
    }

    // Group action bottom sheet
    selectedGroup?.let { group ->
        Dialog(onDismissRequest = { selectedGroup = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(group.name, color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${group.memberCount} members", color = chatTextSec(), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = chatDivider())
                    Spacer(Modifier.height(8.dp))

                    if (!group.isMember) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A2A1A))
                                .clickable { onJoinGroup(group._id); selectedGroup = null; onDismiss() }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.GroupAdd, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                            Column {
                                Text("Join Group", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Is group mein join ho jao", color = chatTextSec(), fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A2A))
                            .clickable { onInviteToGroup(group._id); selectedGroup = null; onDismiss() }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(22.dp))
                        Column {
                            Text("Add Member", color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Kisi ko is group mein invite karo", color = chatTextSec(), fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { selectedGroup = null }, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = chatTextSec()) }
                }
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
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Create Group", color = chatTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).padding(14.dp, 10.dp)) {
                    if (groupName.isEmpty()) Text("Group name...", color = chatTextSec(), fontSize = 13.sp)
                    BasicTextField(value = groupName, onValueChange = { groupName = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(chatSearchBg()).padding(14.dp, 10.dp)) {
                    if (query.isEmpty()) Text("Search members...", color = chatTextSec(), fontSize = 13.sp)
                    BasicTextField(value = query, onValueChange = { query = it; onSearch(it) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = chatTextPrimary(), fontSize = 13.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    itemsIndexed(users) { i, user ->
                        val isSel = selected.contains(user._id)
                        Row(Modifier.fillMaxWidth().clickable { if (isSel) selected.remove(user._id) else selected.add(user._id) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(checked = isSel, onCheckedChange = { if (it) selected.add(user._id) else selected.remove(user._id) },
                                colors = CheckboxDefaults.colors(checkedColor = AccentRed, uncheckedColor = TextSec))
                            Box(Modifier.size(32.dp).clip(CircleShape).background(avatarGradient(i)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
                            }
                            Text(user.channelName, color = chatTextPrimary(), fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = chatTextSec()) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (groupName.isNotBlank() && selected.isNotEmpty()) onCreate(groupName.trim(), selected.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        enabled = groupName.isNotBlank() && selected.isNotEmpty()) { Text("Create") }
                }
            }
        }
    }
}

@Composable
fun AiCompanionChatTab(viewModel: ChatViewModel) {
    Box(
        Modifier.fillMaxSize().background(chatBg()).navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🤖", fontSize = 64.sp)
            Text(
                "AI Companion",
                color = chatTextPrimary(),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFF4A1A8A))))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Coming Soon", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Hamara AI assistant jald aa raha hai.\nYT-Booster ke saath connected rahein!",
                color = chatTextSec(),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun AttachOption(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(chatCard()), contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = 24.sp)
        }
        Text(label, color = chatTextSec(), fontSize = 11.sp)
    }
}

@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val categoryEmojis = listOf("😊","👋","❤️","🎉","🔥","🍕","🐶")
    val categories = listOf(
        "😊 Smileys" to listOf("😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙","🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨","😐","😑","😶","😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","😟","🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩","🤡","👹","👺","👻","👽","👾","🤖"),
        "👋 Gestures" to listOf("👋","🤚","🖐","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁","👅","👄","💋","🩸"),
        "❤️ Hearts" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️","✝️","☪️","🕉","☸️","✡️","🔯","🕎","☯️","☦️","🛐","⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","🆔","⚛️","🉑","☢️","☣️","📴","📳","🈶","🈚","🈸","🈺","🈷️","✴️","🆚","💮","🉐","㊙️","㊗️","🈴","🈵","🈹","🈲","🅰️","🅱️","🆎","🆑","🅾️","🆘","❌","⭕","🛑","⛔","📛","🚫"),
        "🎉 Fun" to listOf("🎉","🎊","🎈","🎁","🎀","🎗","🎟","🎫","🎖","🏆","🥇","🥈","🥉","🏅","🎪","🤹","🎭","🎨","🎬","🎤","🎧","🎼","🎵","🎶","🎷","🎸","🎹","🎺","🎻","🥁","🪘","🎮","🕹","🎲","♟","🎯","🎳","🎰","🧩","🪅","🪆","🪄","🔮","🧿","🪬","🧸","🪀","🪁","🎠","🎡","🎢","🎪"),
        "🔥 Popular" to listOf("🔥","💯","✨","⭐","🌟","💫","⚡","🌈","🎯","💥","🎆","🎇","🌊","🌸","🌺","🌻","🌹","🌷","🍀","🌿","🍃","🍂","🍁","🌾","🌵","🌴","🌳","🌲","🎋","🎍","🪴","🌱","🌾","🌏","🌍","🌎","🌕","🌖","🌗","🌘","🌑","🌒","🌓","🌔","🌙","🌛","🌜","🌚","🌝","🌞","🪐","⭐","🌟","💫","✨","⚡","🌤","⛅","🌥","☁️","🌦","🌧","⛈","🌩","🌨","❄️","☃️","⛄","🌬","💨","🌀","🌈","🌂","☂️","☔","⛱","⚡","❄️","🔥","💧","🌊"),
        "🍕 Food" to listOf("🍕","🍔","🍟","🌭","🍿","🧂","🥓","🥚","🍳","🧇","🥞","🧈","🍞","🥐","🥖","🫓","🥨","🥯","🧀","🥗","🥙","🥪","🌮","🌯","🫔","🍱","🍘","🍙","🍚","🍛","🍜","🍝","🍠","🍢","🍣","🍤","🍥","🥮","🍡","🥟","🥠","🥡","🦀","🦞","🦐","🦑","🦪","🍦","🍧","🍨","🍩","🍪","🎂","🍰","🧁","🥧","🍫","🍬","🍭","🍮","🍯","🍼","🥛","☕","🫖","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷","🥃","🍸","🍹","🧉","🍾"),
        "🐶 Animals" to listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪲","🦟","🦗","🪳","🕷","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆","🦓","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐩","🦮","🐕‍🦺","🐈","🐈‍⬛","🪶","🐓","🦃","🦤","🦚","🦜","🦢","🦩","🕊","🐇","🦝","🦨","🦡","🦫","🦦","🦥","🐁","🐀","🐿","🦔"),
    )
    var selectedCat by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxWidth().height(280.dp).background(chatCard())) {
        Row(
            Modifier.fillMaxWidth().background(chatCardAlt()).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                categoryEmojis.forEachIndexed { i, emoji ->
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selectedCat == i) AccentRed else Color.Transparent)
                            .clickable { selectedCat = i }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text(text = emoji, fontSize = 18.sp) }
                }
            }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A2E)).clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) { Text(text = "✕", fontSize = 16.sp, color = chatTextSec()) }
        }
        val emojis: List<String> = categories[selectedCat].second
        Box(
            Modifier.fillMaxSize().pointerInput(selectedCat) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -40f && selectedCat < categories.size - 1) selectedCat++
                    else if (dragAmount > 40f && selectedCat > 0) selectedCat--
                }
            }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(emojis.size) { idx ->
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { onEmojiSelected(emojis[idx]) },
                        contentAlignment = Alignment.Center
                    ) { Text(text = emojis[idx], fontSize = 22.sp) }
                }
            }
        }
    }
}

@Composable
fun VoiceRecordButton(onVoiceSent: (java.io.File) -> Unit, onRecordingStateChanged: (Boolean) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<java.io.File?>(null) }
    var elapsedSecs by remember { mutableStateOf(0) }
    var swipeOffset by remember { mutableStateOf(0f) }

    // Timer — runs while recording and not paused
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                elapsedSecs++
            }
        }
    }

    fun startRecording() {
        val file = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        voiceFile = file
        try {
            val rec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") android.media.MediaRecorder()
            }
            rec.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            elapsedSecs = 0
            isPaused = false
            isRecording = true
            onRecordingStateChanged(true)
        } catch (e: Exception) { isRecording = false }
    }

    fun stopAndSend() {
        try { recorder?.stop(); recorder?.release() } catch (e: Exception) { }
        recorder = null
        val f = voiceFile
        isRecording = false; isPaused = false; elapsedSecs = 0; swipeOffset = 0f
        onRecordingStateChanged(false)
        if (f != null && f.exists() && f.length() > 0) onVoiceSent(f)
    }

    fun cancelRecording() {
        try { recorder?.stop(); recorder?.release() } catch (e: Exception) { }
        recorder = null
        voiceFile?.delete(); voiceFile = null
        isRecording = false; isPaused = false; elapsedSecs = 0; swipeOffset = 0f
        onRecordingStateChanged(false)
    }

    fun formatTime(secs: Int) = "%d:%02d".format(secs / 60, secs % 60)

    if (isRecording) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha"
        )
        val waveAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(400),
                repeatMode = RepeatMode.Reverse
            ), label = "wave"
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(chatCardAlt())
                .padding(8.dp, 6.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipeOffset < -80f) cancelRecording() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-200f, 0f)
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF2A1A1A)).clickable { cancelRecording() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, null, tint = AccentRed, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.size(10.dp).clip(CircleShape)
                .background(if (isPaused) Color(0xFFFFAA00) else AccentRed.copy(alpha = pulseAlpha)))
            if (swipeOffset < -20f) {
                // Show slide to cancel hint
                Text(
                    "← Cancel",
                    color = AccentRed.copy(alpha = ((-swipeOffset - 20f) / 60f).coerceIn(0f, 1f)),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(formatTime(elapsedSecs), color = chatTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.4f, 0.7f, 1f, 0.6f, 0.9f, 0.5f, 0.8f, 0.3f).forEach { h ->
                        Box(Modifier.width(3.dp).height((h * 20).dp).clip(RoundedCornerShape(2.dp))
                            .background(AccentRed.copy(alpha = if (isPaused) 0.2f else (waveAlpha * h).coerceIn(0.1f, 1f))))
                    }
                }
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(chatSearchBg()).clickable {
                if (isPaused) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        try { recorder?.resume() } catch (e: Exception) { }
                    }
                    isPaused = false
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        try { recorder?.pause() } catch (e: Exception) { }
                    }
                    isPaused = true
                }
            }, contentAlignment = Alignment.Center) {
                Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (elapsedSecs >= 3) Brush.linearGradient(listOf(AccentRed, Color(0xFFFF6B6B))) else Brush.linearGradient(listOf(Color(0xFF444444), Color(0xFF333333))))
                    .clickable(enabled = elapsedSecs >= 3) { stopAndSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, null, tint = if (elapsedSecs >= 3) Color.White else Color(0xFF666666), modifier = Modifier.size(18.dp))
            }
        }
    } else {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(chatSearchBg())
                .pointerInput(Unit) { detectTapGestures(onLongPress = { startRecording() }) },
            contentAlignment = Alignment.Center
        ) {
            Text("🎤", fontSize = 16.sp)
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
