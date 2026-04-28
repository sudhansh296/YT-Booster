package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.data.FriendUser
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(onBack: () -> Unit = {}) {
    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSec = AppColors.textSecondary(dark)

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
    val token = remember { "Bearer ${prefs.getString("token", "")}" }
    val scope = rememberCoroutineScope()

    var friends by remember { mutableStateOf<List<FriendUser>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<FriendUser>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMsg by remember { mutableStateOf("") }
    var friendIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadFriends() {
        scope.launch {
            try {
                val resp = RetrofitClient.api.getFriends(token)
                friends = resp.friends
                friendIds = resp.friends.map { it._id }.toSet()
            } catch (e: Exception) { }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFriends() }

    fun doSearch(q: String) {
        if (q.length < 2) { searchResults = emptyList(); return }
        scope.launch {
            isSearching = true
            try {
                val resp = RetrofitClient.api.searchFriends(token, q)
                searchResults = resp.users
            } catch (e: Exception) { searchResults = emptyList() }
            isSearching = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
            }
            Text("👥 Friends", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                doSearch(it)
            },
            placeholder = { Text("Search users...", color = textSec) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textSec) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch(searchQuery) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = Color(0xFFFF0000),
                unfocusedBorderColor = textSec.copy(alpha = 0.4f),
                cursorColor = Color(0xFFFF0000)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        if (statusMsg.isNotEmpty()) {
            Text(
                statusMsg,
                color = if (statusMsg.contains("✅")) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
        }

        if (searchQuery.length >= 2) {
            // Search results
            Text("Search Results", color = textSec, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (isSearching) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchResults) { user ->
                        val isFriend = user._id in friendIds
                        FriendUserCard(
                            user = user, dark = dark, textColor = textColor, textSec = textSec, card = card,
                            isFriend = isFriend,
                            onAdd = {
                                scope.launch {
                                    try {
                                        RetrofitClient.api.addFriend(token, user._id)
                                        statusMsg = "✅ ${user.channelName} added!"
                                        loadFriends()
                                    } catch (e: Exception) { statusMsg = "❌ ${e.message?.take(40)}" }
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    try {
                                        RetrofitClient.api.removeFriend(token, user._id)
                                        statusMsg = "Removed ${user.channelName}"
                                        loadFriends()
                                    } catch (e: Exception) { statusMsg = "❌ ${e.message?.take(40)}" }
                                }
                            }
                        )
                    }
                    if (searchResults.isEmpty()) {
                        item { Text("No users found", color = textSec, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                }
            }
        } else {
            // Friends list
            Text(
                "My Friends (${friends.size})",
                color = textSec, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
            } else if (friends.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("👥", fontSize = 40.sp)
                        Text("Koi friend nahi hai abhi", color = textSec, fontSize = 14.sp)
                        Text("Search karke friends add karo!", color = textSec, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(friends) { user ->
                        FriendUserCard(
                            user = user, dark = dark, textColor = textColor, textSec = textSec, card = card,
                            isFriend = true,
                            onAdd = {},
                            onRemove = {
                                scope.launch {
                                    try {
                                        RetrofitClient.api.removeFriend(token, user._id)
                                        statusMsg = "Removed ${user.channelName}"
                                        loadFriends()
                                    } catch (e: Exception) { statusMsg = "❌ ${e.message?.take(40)}" }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendUserCard(
    user: FriendUser,
    dark: Boolean,
    textColor: Color,
    textSec: Color,
    card: Color,
    isFriend: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (user.profilePic.isNotEmpty()) {
                AsyncImage(model = user.profilePic, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) {
                    Text(user.channelName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(user.channelName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text("${user.coins} 🪙 • ${user.totalEarned} earned", color = textSec, fontSize = 12.sp)
            }
            if (isFriend) {
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Remove", fontSize = 12.sp) }
            } else {
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Add", fontSize = 12.sp, color = Color.White) }
            }
        }
    }
}
