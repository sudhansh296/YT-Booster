package com.ytsubexchange.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.R
import com.ytsubexchange.data.LeaderboardEntry
import com.ytsubexchange.data.MatchData
import com.ytsubexchange.data.NoticeData
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.MainViewModel
import com.ytsubexchange.viewmodel.QueueState

@Composable
fun HomeScreen(viewModel: MainViewModel, onNavigateToCommunity: () -> Unit = {}, onNavigateToDailyTasks: () -> Unit = {}) {
    val profile by viewModel.profile.collectAsState()
    val queueState by viewModel.queueState.collectAsState()
    val notices by viewModel.notices.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val leaderboardData by viewModel.leaderboard.collectAsState()
    val isLoadingLeaderboard by viewModel.isLoadingLeaderboard.collectAsState()
    var showBuyDialog by remember { mutableStateOf(false) }
    var showBuyCoinsDialog by remember { mutableStateOf(false) }
    var bonusMsg by remember { mutableStateOf("") }

    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSecondary = AppColors.textSecondary(dark)

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("notices", android.content.Context.MODE_PRIVATE) }
    var noticeToShow by remember { mutableStateOf<NoticeData?>(null) }

    LaunchedEffect(notices) {
        if (notices.isNotEmpty()) {
            noticeToShow = notices.firstOrNull { notice ->
                if (notice.subAdminCode != null) {
                    // Subadmin notice — show only once (check seen flag)
                    !prefs.getBoolean("seen_${notice._id}", false)
                } else {
                    // Admin notice — show every time app opens (no seen check)
                    true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.leaderboard.value == null) viewModel.loadLeaderboard()
    }

    Box(modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.subscribers_icon),
                contentDescription = "YT-Booster",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
            )

            // Update banner - sirf tab dikhao jab update available ho
            updateInfo?.let { info ->
                Button(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl))
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🆕", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update Available - v${info.versionName}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap karke update karo", color = Color(0xFF888888), fontSize = 11.sp)
                        }
                        Text("↗", color = Color(0xFF4CAF50), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            profile?.let { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = p.profilePic, contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.channelName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text("${p.coins} Coins", color = Color(0xFFFFD700), fontSize = 14.sp)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Given", p.subscribersGiven.toString(), Modifier.weight(1f), dark)
                    StatCard("Received", p.subscribersReceived.toString(), Modifier.weight(1f), dark)
                    StatCard("Earned", p.totalEarned.toString(), Modifier.weight(1f), dark)
                }
            }

            when (val state = queueState) {
                is QueueState.Idle -> {
                    Button(
                        onClick = { viewModel.joinQueue() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶ Join Queue & Earn Coins", color = Color.White, modifier = Modifier.padding(8.dp))
                    }
                }
                is QueueState.Waiting -> {
                    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFFF0000))
                            Spacer(Modifier.height(10.dp))
                            Text("Match dhundh raha hai...", color = textColor)
                            if (state.queueSize > 0) Text("Queue mein: ${state.queueSize} users", color = textSecondary, fontSize = 13.sp)
                            TextButton(onClick = { viewModel.leaveQueue() }) { Text("Leave Queue", color = textSecondary) }
                        }
                    }
                }
                is QueueState.InQueue -> {
                    // Show max 3 channels, sequential subscribe (one at a time)
                    val channels = state.channels.take(3)
                    var currentIndex by remember(state) { mutableStateOf(0) }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Subscribe karo, Coins kamao", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            TextButton(onClick = { viewModel.leaveQueue() }) { Text("Leave", color = textSecondary, fontSize = 12.sp) }
                        }
                        Text("${currentIndex}/${channels.size} complete • Queue: ${state.queueSize}", color = textSecondary, fontSize = 12.sp)

                        channels.forEachIndexed { idx, channel ->
                            val isActive = idx == currentIndex
                            val isDone = idx < currentIndex
                            QueueChannelCard(
                                channel = channel,
                                viewModel = viewModel,
                                dark = dark,
                                isActive = isActive,
                                isDone = isDone,
                                onSubscribed = { currentIndex++ }
                            )
                        }
                    }
                }
                is QueueState.Matched -> MatchCard(state, viewModel, dark)
                is QueueState.CoinEarned -> {
                    Card(colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉 +${state.earned} Coin${if (state.earned > 1) "s" else ""} Mila!", color = Color(0xFF4CAF50), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Total: ${state.totalCoins} coins", color = textColor)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.joinQueue() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))) { Text("Join Again") }
                        }
                    }
                }
                is QueueState.VerifyFailed -> {
                    Card(colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF2A1A1A) else Color(0xFFFFEBEE)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Subscription not verified", color = Color.Red)
                            Button(onClick = { viewModel.joinQueue() }) { Text("Try Again") }
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.claimDailyBonus { _, msg -> bonusMsg = msg } },
                colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF1A1A3A) else Color(0xFFE3F2FD)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🎁 Daily Bonus Claim Karo", color = Color(0xFF29B6F6), modifier = Modifier.padding(6.dp))
            }
            if (bonusMsg.isNotEmpty()) {
                Text(bonusMsg, color = if (bonusMsg.contains("+")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            // Daily Tasks button
            Button(
                onClick = { onNavigateToDailyTasks() },
                colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF1A0A2E) else Color(0xFFF3E5F5)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🎯 Daily Tasks", color = Color(0xFF7B2FF7), modifier = Modifier.padding(6.dp))
            }

            // Live Events banner
            val scope2 = rememberCoroutineScope()
            val context2 = androidx.compose.ui.platform.LocalContext.current
            val authPrefs2 = remember { context2.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE) }
            val token2 = remember { "Bearer ${authPrefs2.getString("token", "")}" }
            var activeEvents by remember { mutableStateOf<List<com.ytsubexchange.data.LiveEvent>>(emptyList()) }
            LaunchedEffect(Unit) {
                try {
                    val resp = com.ytsubexchange.network.RetrofitClient.api.getUpcomingEvents(token2)
                    activeEvents = resp.events
                } catch (e: Exception) { }
            }
            activeEvents.forEach { event ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFF6F00).copy(0.3f), Color(0xFFFFD600).copy(0.2f))))
                    .padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(event.icon, fontSize = 24.sp)
                        Column(Modifier.weight(1f)) {
                            Text(event.title, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (event.description.isNotEmpty()) Text(event.description, color = Color(0xFFFFAA00), fontSize = 11.sp)
                        }
                        if (event.type == "double_coins") {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF6F00)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("${event.multiplier}x 🪙", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showBuyCoinsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF1A3A1A) else Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💰 Buy Coins", color = Color(0xFFFFD700), modifier = Modifier.padding(6.dp))
            }

            // Community Chat button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dark) Color(0xFF0D1B2A) else Color(0xFFE3F2FD))
                    .clickable { onNavigateToCommunity() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("\uD83D\uDCAC", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Community Chat", color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE53935))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Button(
                onClick = { showBuyDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF2A1A00) else Color(0xFFFFF8E1)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀 Use Coins → Get Subscribers", color = Color(0xFFFFD700), modifier = Modifier.padding(6.dp))
            }

            // TOP EARNERS section
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🏆", fontSize = 14.sp)
                    Text("TOP EARNERS", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                if (isLoadingLeaderboard) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                } else if (leaderboardData == null) {
                    Card(colors = CardDefaults.cardColors(containerColor = AppColors.card(dark)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Load nahi hua", color = textSecondary, fontSize = 13.sp)
                            TextButton(onClick = { viewModel.loadLeaderboard() }) { Text("Retry", color = Color(0xFFFF0000), fontSize = 12.sp) }
                        }
                    }
                } else {
                    val data = leaderboardData!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF1A1A3A) else Color(0xFFE8EAF6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp, 10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Meri Rank", color = textSecondary, fontSize = 13.sp)
                            Text("#${data.myRank}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("${data.myEarned} coins", color = Color(0xFF29B6F6), fontSize = 13.sp)
                        }
                    }
                    data.leaderboard.take(3).forEachIndexed { index, entry ->
                        MiniLeaderboardRow(rank = index + 1, entry = entry, isMe = false, dark = dark)
                    }
                    if (data.myRank > 3) {
                        profile?.let { p ->
                            MiniLeaderboardRow(
                                rank = data.myRank,
                                entry = LeaderboardEntry(
                                    _id = "", channelName = "You",
                                    profilePic = p.profilePic,
                                    totalEarned = data.myEarned,
                                    subscribersGiven = p.subscribersGiven,
                                    subscribersReceived = p.subscribersReceived
                                ),
                                isMe = true, dark = dark
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    noticeToShow?.let { notice ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = if (dark) Color(0xFF1A1A2E) else Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (notice.subAdminCode != null) "📢" else "🔔", fontSize = 18.sp)
                    Text(notice.title, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(notice.message, color = textColor, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    if (notice.subAdminCode != null) {
                        // Subadmin notice — mark as seen so it doesn't show again
                        prefs.edit().putBoolean("seen_${notice._id}", true).apply()
                        noticeToShow = notices.firstOrNull { n ->
                            n._id != notice._id && n.subAdminCode != null && !prefs.getBoolean("seen_${n._id}", false)
                        }
                    } else {
                        // Admin notice — just dismiss, will show again next time
                        noticeToShow = null
                    }
                }) { Text("OK", color = Color(0xFFFFD700)) }
            }
        )
    }

    if (showBuyCoinsDialog) {
        AlertDialog(
            onDismissRequest = { showBuyCoinsDialog = false },
            containerColor = card,
            title = { Text("💰 Buy Coins", color = Color(0xFFFFD700)) },
            text = { Text("Coins kharidne ke liye Telegram pe contact karo:", color = textSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/GODCHEATOFFICIAL"))
                    context.startActivity(intent)
                    showBuyCoinsDialog = false
                }) { Text("Telegram pe Contact →", color = Color(0xFF29B6F6)) }
            },
            dismissButton = { TextButton(onClick = { showBuyCoinsDialog = false }) { Text("Cancel", color = textSecondary) } }
        )
    }

    if (showBuyDialog) {
        var orderStatus by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isLoading) showBuyDialog = false },
            containerColor = card,
            title = { Text("🚀 Get Subscribers", color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Apna plan chuno:", color = textSecondary, fontSize = 13.sp)
                    if (orderStatus.isNotEmpty()) {
                        Text(orderStatus, color = if (orderStatus.contains("✅")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 13.sp)
                    }
                    listOf(Triple(1000, 100, ""), Triple(5000, 500, ""), Triple(10000, 1500, "🔥 Best Value")).forEach { (coins, subs, tag) ->
                        val hasEnough = (profile?.coins ?: 0) >= coins
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppColors.cardAlt(dark)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("$subs Subscribers", color = textColor, fontWeight = FontWeight.Bold)
                                        if (tag.isNotEmpty()) Text(tag, color = Color(0xFFFF6B00), fontSize = 11.sp)
                                    }
                                    Text("$coins Coins", color = Color(0xFFFFD700), fontSize = 13.sp)
                                }
                                Button(
                                    onClick = {
                                        isLoading = true; orderStatus = "Processing..."
                                        viewModel.buySubscribers(coins) { success, msg ->
                                            isLoading = false
                                            orderStatus = if (success) "✅ $msg" else "❌ $msg"
                                        }
                                    },
                                    enabled = hasEnough && !isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), disabledContainerColor = if (dark) Color(0xFF333333) else Color(0xFFCCCCCC)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    else Text(if (hasEnough) "Redeem" else "Need $coins", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { if (!isLoading) showBuyDialog = false }) { Text("Cancel", color = textSecondary) } }
        )
    }
}

@Composable
fun QueueChannelCard(channel: MatchData, viewModel: MainViewModel, dark: Boolean, isActive: Boolean = true, isDone: Boolean = false, onSubscribed: (() -> Unit)? = null) {
    val context = LocalContext.current
    var subscribeClicked by remember(channel.matchId) { mutableStateOf(false) }
    var claimed by remember(channel.matchId) { mutableStateOf(false) }
    var timerSeconds by remember(channel.matchId) { mutableStateOf(30) }
    var timerDone by remember(channel.matchId) { mutableStateOf(false) }

    LaunchedEffect(subscribeClicked) {
        if (subscribeClicked && !timerDone) {
            for (i in 30 downTo 1) {
                timerSeconds = i
                kotlinx.coroutines.delay(1000)
            }
            timerDone = true
        }
    }

    val cardAlpha = if (isDone) 0.5f else if (!isActive) 0.6f else 1f

    Card(
        colors = CardDefaults.cardColors(containerColor = when {
            isDone -> (if (dark) Color(0xFF1A2A1A) else Color(0xFFE8F5E9))
            !isActive -> AppColors.card(dark).copy(alpha = 0.6f)
            else -> AppColors.card(dark)
        }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = channel.profilePic, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                // Card type badge
                val (badgeText, badgeColor) = when (channel.cardType) {
                    "owner" -> "⭐ Featured" to Color(0xFFFFD700)
                    "p2p" -> "🔄 P2P Match" to Color(0xFF29B6F6)
                    "online" -> "🟢 Online Now" to Color(0xFF4CAF50)
                    else -> "" to Color.Transparent
                }
                if (badgeText.isNotEmpty()) {
                    Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(channel.channelName, color = if (isDone) Color(0xFF4CAF50) else AppColors.text(dark), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text("+${channel.coinsReward} coin${if (channel.coinsReward > 1) "s" else ""}", color = Color(0xFFFFD700), fontSize = 12.sp)
            }
            when {
                isDone -> Text("✅ Done", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                !isActive -> Text("🔒 Wait", color = AppColors.textSecondary(dark), fontSize = 12.sp)
                claimed -> Text("✅ Done", color = Color(0xFF4CAF50), fontSize = 12.sp)
                !subscribeClicked -> Button(
                    onClick = {
                        subscribeClicked = true
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(channel.channelUrl)))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Subscribe", color = Color.White, fontSize = 12.sp) }
                !timerDone -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    CircularProgressIndicator(progress = timerSeconds / 30f, color = Color(0xFFFF0000), trackColor = AppColors.cardAlt(dark), strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                    Text("$timerSeconds", color = AppColors.text(dark), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                else -> Button(
                    onClick = {
                        claimed = true
                        viewModel.confirmSubscribe(channel.matchId)
                        onSubscribed?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Claim +${channel.coinsReward}", color = Color.White, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, dark: Boolean = true) {
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.card(dark)), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = AppColors.text(dark), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = AppColors.textSecondary(dark), fontSize = 11.sp)
        }
    }
}

@Composable
fun MiniLeaderboardRow(rank: Int, entry: com.ytsubexchange.data.LeaderboardEntry, isMe: Boolean, dark: Boolean) {
    val rankEmoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }
    val rankColor = when (rank) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFB0BEC5); 3 -> Color(0xFFCD7F32); else -> Color(0xFFFF0000) }
    val bgColor = if (isMe)
        (if (dark) Color(0xFF2A1A1A) else Color(0xFFFFEBEE))
    else
        AppColors.card(dark)
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        border = if (isMe) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0000)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rankEmoji, fontSize = if (rank <= 3) 20.sp else 14.sp, color = rankColor, modifier = Modifier.width(32.dp))
            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                if (entry.profilePic.isNotEmpty()) {
                    AsyncImage(model = entry.profilePic, contentDescription = null, modifier = Modifier.size(38.dp).clip(CircleShape))
                } else {
                    Text(if (isMe) "⭐" else "👤", fontSize = 16.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.channelName, color = AppColors.text(dark), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text("Given: ${entry.subscribersGiven}", color = AppColors.textSecondary(dark), fontSize = 11.sp)
            }
            Text("${entry.totalEarned}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
