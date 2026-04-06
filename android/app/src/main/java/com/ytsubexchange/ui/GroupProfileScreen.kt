package com.ytsubexchange.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ytsubexchange.data.GroupInfoResponse
import com.ytsubexchange.data.GroupMember
import com.ytsubexchange.viewmodel.ChatViewModel

private val BgD = Color(0xFF0D0D0D)
private val CardD = Color(0xFF1A1A2E)
private val CardA = Color(0xFF16213E)
private val AccR = Color(0xFFE53935)
private val TxtP = Color(0xFFFFFFFF)
private val TxtS = Color(0xFF9E9E9E)
private val Div2 = Color(0xFF1E1E2E)

@Composable
fun GroupProfileScreen(
    groupInfo: GroupInfoResponse,
    viewModel: ChatViewModel,
    roomId: String,
    myId: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var selectedMember by remember { mutableStateOf<GroupMember?>(null) }
    var showSubAdminPerms by remember { mutableStateOf(false) }
    var subAdminTarget by remember { mutableStateOf<GroupMember?>(null) }
    var showEditName by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf(groupInfo.room.name) }
    var inviteLink by remember { mutableStateOf("") }
    var showInviteLinkDialog by remember { mutableStateOf(false) }

    var showWallpaperPicker by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var addMemberQuery by remember { mutableStateOf("") }
    var addMemberUsers by remember { mutableStateOf<List<com.ytsubexchange.data.ChatUser>>(emptyList()) }
    var permDeleteMsg by remember { mutableStateOf(true) }
    var permBanMembers by remember { mutableStateOf(false) }
    var permInviteMembers by remember { mutableStateOf(true) }
    var permPinMessages by remember { mutableStateOf(false) }
    var permChangeInfo by remember { mutableStateOf(false) }
    var permStartVoiceChat by remember { mutableStateOf(false) }

    val subAdmins = groupInfo.room.subAdmins ?: emptyList()
    val picLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updateGroupPic(roomId, it, ctx) }
    }

    Column(
        Modifier.fillMaxSize().background(BgD).statusBarsPadding().navigationBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(CardD).padding(4.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = TxtP)
            }
            Text("Group Info", color = TxtP, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            // Group avatar + name header
            item {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(listOf(Color(0xFF1A1A3E), BgD))
                    ).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (!groupInfo.room.pic.isNullOrEmpty()) {
                                AsyncImage(
                                    model = groupInfo.room.pic,
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp).clip(CircleShape)
                                )
                            } else {
                                Box(
                                    Modifier.size(100.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(AccR, Color(0xFFFF6B6B)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                            }
                            if (groupInfo.isAdmin) {
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(AccR)
                                        .clickable { picLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(groupInfo.room.name, color = TxtP, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            if (groupInfo.isAdmin) {
                                Icon(Icons.Default.Edit, null, tint = TxtS, modifier = Modifier.size(18.dp).clickable { showEditName = true })
                            }
                        }
                        Text("${groupInfo.members.size} members", color = TxtS, fontSize = 14.sp)
                        if (!groupInfo.room.description.isNullOrEmpty()) {
                            Text(groupInfo.room.description!!, color = TxtS, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Admin quick actions
            if (groupInfo.isAdmin) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Admin Actions", color = AccR, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Invite link
                            ActionCard(
                                icon = Icons.Default.Link,
                                label = "Invite Link",
                                color = Color(0xFF29B6F6),
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.getGroupInviteLink(roomId) { link ->
                                    inviteLink = link
                                    showInviteLinkDialog = true
                                }
                            }

                            ActionCard(
                                icon = Icons.Default.PersonAdd,
                                label = "Add Member",
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.weight(1f)
                            ) { showAddMemberDialog = true }

                            // Wallpaper
                            ActionCard(
                                icon = Icons.Default.Wallpaper,
                                label = "Wallpaper",
                                color = Color(0xFF9C27B0),
                                modifier = Modifier.weight(1f)
                            ) { showWallpaperPicker = true }
                        }
                    }
                    Divider(color = Div2)
                }
            } else {
                // Non-admin: just invite link + wallpaper
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(
                            icon = Icons.Default.Wallpaper,
                            label = "Wallpaper",
                            color = Color(0xFF9C27B0),
                            modifier = Modifier.weight(1f)
                        ) { showWallpaperPicker = true }
                    }
                    Divider(color = Div2)
                }
            }

            // Members section
            item {
                Text(
                    "Members (${groupInfo.members.size})",
                    color = AccR, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 6.dp)
                )
            }

            items(groupInfo.members) { member ->
                val isSubAdmin = subAdmins.any { it.userId == member._id }
                val isOwner = groupInfo.isAdmin && member._id == myId
                val role = when {
                    isOwner -> "Owner"
                    isSubAdmin -> "Admin ⭐"
                    else -> "Member"
                }
                val roleColor = when {
                    isOwner -> Color(0xFFFFD700)
                    isSubAdmin -> Color(0xFF29B6F6)
                    else -> TxtS
                }

                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = groupInfo.isAdmin && member._id != myId) { selectedMember = member }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFF4A1A8A)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (member.profilePic.isNotEmpty()) {
                            AsyncImage(model = member.profilePic, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Icon(Icons.Default.Person, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(member.channelName, color = TxtP, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(role, color = roleColor, fontSize = 12.sp)
                    }
                    if (groupInfo.isAdmin && member._id != myId) {
                        Icon(Icons.Default.MoreVert, null, tint = TxtS, modifier = Modifier.size(18.dp))
                    }
                }
                Divider(color = Div2, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Leave group (non-admin)
            if (!groupInfo.isAdmin) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.leaveGroup { onBack() } }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                        Text("Leave Group", color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Edit group name dialog
    if (showEditName) {
        Dialog(onDismissRequest = { showEditName = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Group Name Change Karo", color = TxtP, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = editNameText,
                        onValueChange = { editNameText = it },
                        label = { Text("Group Name", color = TxtS) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccR, unfocusedBorderColor = Div2,
                            focusedTextColor = TxtP, unfocusedTextColor = TxtP
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEditName = false }) { Text("Cancel", color = TxtS) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editNameText.isNotBlank()) {
                                    viewModel.updateGroupName(roomId, editNameText.trim())
                                    showEditName = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccR)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }

    // Invite link dialog
    if (showInviteLinkDialog && inviteLink.isNotEmpty()) {
        Dialog(onDismissRequest = { showInviteLinkDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🔗 Invite Link", color = TxtP, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // Link display box
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(CardA).padding(12.dp)
                    ) {
                        Text(inviteLink, color = Color(0xFF29B6F6), fontSize = 12.sp)
                    }

                    // Copy button
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A2A1A))
                            .clickable {
                                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("invite_link", inviteLink))
                                showInviteLinkDialog = false
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Text("Copy Link", color = TxtP, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Share button
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A2A))
                            .clickable {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Join my group: $inviteLink")
                                }
                                ctx.startActivity(android.content.Intent.createChooser(intent, "Share invite link"))
                                showInviteLinkDialog = false
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Share, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(20.dp))
                        Text("Share Link", color = TxtP, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(onClick = { showInviteLinkDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = TxtS)
                    }
                }
            }
        }
    }

    // Add Member dialog
    if (showAddMemberDialog) {
        Dialog(onDismissRequest = { showAddMemberDialog = false; addMemberQuery = ""; addMemberUsers = emptyList() }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("👤 Add Member", color = TxtP, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = addMemberQuery,
                        onValueChange = { q ->
                            addMemberQuery = q
                            if (q.length >= 2) {
                                viewModel.searchUsersForGroup(q) { users -> addMemberUsers = users }
                            } else {
                                addMemberUsers = emptyList()
                            }
                        },
                        label = { Text("User search karo", color = TxtS) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccR, unfocusedBorderColor = Div2,
                            focusedTextColor = TxtP, unfocusedTextColor = TxtP
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addMemberUsers.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            items(count = addMemberUsers.size) { i ->
                                val user = addMemberUsers[i]
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.inviteMemberToGroup(roomId, user._id)
                                            showAddMemberDialog = false
                                            addMemberQuery = ""
                                            addMemberUsers = emptyList()
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccR, Color(0xFFFF6B6B)))), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(user.channelName, color = TxtP, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Tap to invite", color = TxtS, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else if (addMemberQuery.length >= 2) {
                        Text("Koi user nahi mila", color = TxtS, fontSize = 13.sp)
                    }
                    TextButton(onClick = { showAddMemberDialog = false; addMemberQuery = ""; addMemberUsers = emptyList() }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = TxtS)
                    }
                }
            }
        }
    }

    // Wallpaper picker dialog
    if (showWallpaperPicker) {
        WallpaperPickerDialog(
            roomId = roomId,
            viewModel = viewModel,
            onDismiss = { showWallpaperPicker = false }
        )
    }

    // Member action sheet
    selectedMember?.let { member ->
        val isSubAdmin = subAdmins.any { it.userId == member._id }
        Dialog(onDismissRequest = { selectedMember = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccR, Color(0xFFFF6B6B)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(member.channelName, color = TxtP, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (isSubAdmin) "Admin ⭐" else "Member", color = if (isSubAdmin) Color(0xFF29B6F6) else TxtS, fontSize = 12.sp)
                        }
                    }
                    Divider(color = Div2)

                    MemberActionRow(Icons.Default.AdminPanelSettings, Color(0xFF29B6F6),
                        if (isSubAdmin) "Edit Admin Rights" else "Make Admin") {
                        subAdminTarget = member
                        val existing = subAdmins.firstOrNull { it.userId == member._id }
                        permDeleteMsg = existing?.canDeleteMessages ?: true
                        permBanMembers = existing?.canBanMembers ?: false
                        permInviteMembers = existing?.canInviteMembers ?: true
                        permPinMessages = existing?.canPinMessages ?: false
                        permChangeInfo = existing?.canChangeGroupInfo ?: false
                        permStartVoiceChat = existing?.canStartVoiceChat ?: false
                        showSubAdminPerms = true
                        selectedMember = null
                    }

                    if (isSubAdmin) {
                        MemberActionRow(Icons.Default.RemoveModerator, Color(0xFFFF9800), "Remove Admin") {
                            viewModel.manageSubAdmin(roomId, member._id, "remove")
                            selectedMember = null
                        }
                    }

                    MemberActionRow(Icons.Default.PersonRemove, Color(0xFFFF6B6B), "Remove from Group") {
                        viewModel.removeMemberFromGroup(roomId, member._id)
                        selectedMember = null
                    }

                    TextButton(onClick = { selectedMember = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = TxtS)
                    }
                }
            }
        }
    }

    // SubAdmin permissions dialog
    if (showSubAdminPerms && subAdminTarget != null) {
        Dialog(onDismissRequest = { showSubAdminPerms = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CardD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("⭐ Admin Rights", color = TxtP, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subAdminTarget!!.channelName, color = Color(0xFF29B6F6), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        Triple("Delete Messages", permDeleteMsg, { v: Boolean -> permDeleteMsg = v }),
                        Triple("Ban Members", permBanMembers, { v: Boolean -> permBanMembers = v }),
                        Triple("Invite Members", permInviteMembers, { v: Boolean -> permInviteMembers = v }),
                        Triple("Pin Messages", permPinMessages, { v: Boolean -> permPinMessages = v }),
                        Triple("Change Group Info", permChangeInfo, { v: Boolean -> permChangeInfo = v }),
                        Triple("Start Voice Chat 🎙️", permStartVoiceChat, { v: Boolean -> permStartVoiceChat = v })
                    ).forEach { (label, value, onChange) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = TxtP, fontSize = 14.sp)
                            Switch(checked = value, onCheckedChange = onChange,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccR, uncheckedTrackColor = Color(0xFF333333)))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showSubAdminPerms = false }) { Text("Cancel", color = TxtS) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.manageSubAdmin(roomId, subAdminTarget!!._id, "add",
                                    mapOf("canDeleteMessages" to permDeleteMsg, "canBanMembers" to permBanMembers,
                                        "canInviteMembers" to permInviteMembers, "canPinMessages" to permPinMessages,
                                        "canChangeGroupInfo" to permChangeInfo, "canStartVoiceChat" to permStartVoiceChat))
                                showSubAdminPerms = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccR)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(CardA).clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(label, color = TxtP, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MemberActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardA).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = TxtP, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WallpaperPickerDialog(roomId: String, viewModel: ChatViewModel, onDismiss: () -> Unit) {
    // (name, gradient colors, emoji label)
    val wallpapers = listOf(
        Triple("default",        listOf(Color(0xFF0D0D0D), Color(0xFF0D0D0D)),                    "⬛ Default"),
        Triple("midnight_blue",  listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E), Color(0xFF0D0D1A)), "🌌 Midnight"),
        Triple("deep_purple",    listOf(Color(0xFF1A0A2E), Color(0xFF3D1A6E), Color(0xFF1A0A2E)), "💜 Purple"),
        Triple("ocean",          listOf(Color(0xFF001A2E), Color(0xFF003D5C), Color(0xFF001A2E)), "🌊 Ocean"),
        Triple("forest",         listOf(Color(0xFF0A1A0A), Color(0xFF1A3A1A), Color(0xFF0A1A0A)), "🌿 Forest"),
        Triple("sunset",         listOf(Color(0xFF2E0A0A), Color(0xFF5C1A00), Color(0xFF2E0A0A)), "🌅 Sunset"),
        Triple("rose_gold",      listOf(Color(0xFF2E1A1A), Color(0xFF5C2A2A), Color(0xFF2E1A1A)), "🌹 Rose"),
        Triple("galaxy",         listOf(Color(0xFF0A0A1A), Color(0xFF1A0A3E), Color(0xFF2E0A2E)), "🌠 Galaxy"),
        Triple("aurora",         listOf(Color(0xFF001A1A), Color(0xFF003D2E), Color(0xFF001A3D)), "🌈 Aurora"),
        Triple("lava",           listOf(Color(0xFF1A0000), Color(0xFF3D0A00), Color(0xFF1A0000)), "🔥 Lava"),
        Triple("teal_dark",      listOf(Color(0xFF001A1A), Color(0xFF004D4D), Color(0xFF001A1A)), "🩵 Teal"),
        Triple("indigo_night",   listOf(Color(0xFF0A0A2E), Color(0xFF1A1A5E), Color(0xFF0A0A2E)), "🔷 Indigo"),
        Triple("emerald",        listOf(Color(0xFF001A0A), Color(0xFF004D1A), Color(0xFF001A0A)), "💚 Emerald"),
        Triple("crimson",        listOf(Color(0xFF1A0000), Color(0xFF4D0000), Color(0xFF1A0000)), "❤️ Crimson"),
        Triple("cosmic",         listOf(Color(0xFF0A001A), Color(0xFF1A003D), Color(0xFF2E001A)), "✨ Cosmic"),
        Triple("slate",          listOf(Color(0xFF0A0F14), Color(0xFF1A2A3A), Color(0xFF0A0F14)), "🩶 Slate"),
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = CardD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("🎨 Chat Wallpaper", color = TxtP, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Apna favourite theme choose karo", color = TxtS, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(wallpapers.size) { i ->
                        val (name, colors, label) = wallpapers[i]
                        val gradient = if (colors.size == 1) Brush.linearGradient(listOf(colors[0], colors[0]))
                                       else Brush.linearGradient(colors)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                                    .background(gradient)
                                    .clickable { viewModel.setChatWallpaper(roomId, name); onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label.split(" ")[0], fontSize = 22.sp)
                            }
                            Text(
                                label.split(" ").drop(1).joinToString(" "),
                                color = TxtS, fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = TxtS) }
            }
        }
    }
}
