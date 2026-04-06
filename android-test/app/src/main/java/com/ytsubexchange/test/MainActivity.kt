package com.ytsubexchange.test

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.test.ui.*
import com.ytsubexchange.test.ui.theme.AppColors
import com.ytsubexchange.test.ui.theme.isDarkTheme
import com.ytsubexchange.test.viewmodel.MainViewModel
import com.ytsubexchange.test.viewmodel.ChatViewModel

sealed class Screen {
    object Home : Screen()
    object Chat : Screen()
    object Referral : Screen()
    object Exchange : Screen()
    object Profile : Screen()
    // Sub-screens (no bottom nav)
    object Streak : Screen()
    object Settings : Screen()
    object History : Screen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private var tokenState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        isDarkTheme.value = prefs.getBoolean("dark_theme", true)

        // Check for app update in background
        checkForUpdate()
        // Register FCM token
        registerFcmToken()

        setContent {
            val authPrefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val token = remember { mutableStateOf(authPrefs.getString("token", null)) }
            tokenState = token
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
            var openCommunityTab by remember { mutableStateOf(false) }

            if (token.value != null) {
                LaunchedEffect(token.value) { viewModel.init(token.value!!) }

                // Referral popup for new users
                val authPrefs2 = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val pendingRef = remember { mutableStateOf(authPrefs2.getString("pending_ref", null)) }
                val pendingCoins = authPrefs2.getString("pending_ref_coins", "20") ?: "20"

                if (pendingRef.value != null) {
                    val isAlreadyEarned = authPrefs2.getBoolean("pending_ref_earned", false)
                    ReferralOnboardingPopup(
                        prefilledCode = if (pendingRef.value == "ASK") "" else pendingRef.value!!,
                        viewModel = viewModel,
                        alreadyEarned = isAlreadyEarned,
                        earnedCoins = pendingCoins,
                        onDone = {
                            authPrefs2.edit()
                                .remove("pending_ref")
                                .remove("pending_ref_coins")
                                .remove("pending_ref_earned")
                                .apply()
                            pendingRef.value = null
                        }
                    )
                }

                // Sub-screens (full screen, no bottom nav)
                when (currentScreen) {
                    is Screen.Streak -> {
                        StreakScreen(viewModel) { currentScreen = Screen.Profile }
                        return@setContent
                    }
                    is Screen.Settings -> {
                        SettingsScreen { currentScreen = Screen.Profile }
                        return@setContent
                    }
                    is Screen.History -> {
                        TransactionScreen(viewModel, onBack = { currentScreen = Screen.Profile })
                        return@setContent
                    }
                    else -> {}
                }

                // Main screens with bottom nav
                val dark by isDarkTheme
                Box(modifier = Modifier.fillMaxSize().background(AppColors.bg(dark))) {
                    // Content area
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 68.dp)) {
                        when (currentScreen) {
                            is Screen.Home -> HomeScreen(viewModel, onNavigateToChat = { currentScreen = Screen.Chat }, onNavigateToCommunity = { currentScreen = Screen.Chat; openCommunityTab = true })
                            is Screen.Chat -> ChatScreen(chatViewModel, onBack = { currentScreen = Screen.Home }, openCommunity = openCommunityTab.also { openCommunityTab = false })
                            is Screen.Referral -> ReferralScreen(viewModel)
                            is Screen.Exchange -> ExchangeScreen(viewModel)
                            is Screen.Profile -> ProfileScreen(
                                viewModel = viewModel,
                                onNavigateToStreak = { currentScreen = Screen.Streak },
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onNavigateToHistory = { currentScreen = Screen.History }
                            )
                            else -> {}
                        }
                    }

                    // Bottom Navigation Bar
                    BottomNavBar(
                        currentScreen = currentScreen,
                        onSelect = { currentScreen = it },
                        dark = dark,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            } else {
                LoginScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val prefs = getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("fcm_token", token).apply()
                val authToken = prefs.getString("token", null) ?: return@addOnSuccessListener
                Thread {
                    try {
                        com.ytsubexchange.test.network.RetrofitClient.api
                            .updateFcmToken("Bearer $authToken", mapOf("fcmToken" to token))
                            .execute()
                    } catch (e: Exception) { /* silent */ }
                }.start()
            }
    }

    private fun checkForUpdate() {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
        Thread {
            try {
                val versionInfo = com.ytsubexchange.test.network.RetrofitClient.api.checkVersion().execute().body()
                    ?: return@Thread
                if (versionInfo.latestVersion > currentVersion) {
                    runOnUiThread { viewModel.setUpdateAvailable(versionInfo) }
                }
            } catch (e: Exception) { /* silent */ }
        }.start()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            val scheme = uri.scheme ?: return
            val host = uri.host ?: return

            // ytsubexchange://auth?error=...
            if (host == "auth" && uri.getQueryParameter("error") != null) {
                val error = uri.getQueryParameter("error")
                val (title, message, actionLabel, actionUrl) = when (error) {
                    "no_channel" -> listOf(
                        "YouTube Channel Nahi Mila",
                        "Is Google account mein koi YouTube channel nahi hai.\n\nYT Booster use karne ke liye pehle YouTube channel banana zaroori hai.",
                        "Channel Banao",
                        "https://www.youtube.com/create_channel"
                    )
                    "suspended" -> listOf(
                        "YouTube Account Suspended",
                        "Tumhara YouTube account suspend hai. Suspended account se YT Booster use nahi kar sakte.\n\nKoi aur Google account try karo.",
                        "YouTube Help",
                        "https://support.google.com/youtube"
                    )
                    "terminated" -> listOf(
                        "YouTube Channel Terminated",
                        "Tumhara YouTube channel terminate ho chuka hai. Terminated channel se YT Booster use nahi kar sakte.\n\nKoi aur Google account try karo.",
                        "YouTube Help",
                        "https://support.google.com/youtube"
                    )
                    else -> listOf(
                        "Login Failed",
                        "Login karne mein problem aayi. Dobara try karo.",
                        null, null
                    )
                }
                android.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(actionLabel ?: "OK") { _, _ ->
                        if (actionUrl != null) {
                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(actionUrl)))
                        }
                    }
                    .apply { if (actionUrl != null) setNegativeButton("Cancel") { d, _ -> d.dismiss() } }
                    .show()
                return
            }

            // ytsubexchange://ref/TOKEN - landing page se app open hua
            if (host == "ref") {
                val refToken = uri.pathSegments.firstOrNull() ?: uri.lastPathSegment
                if (refToken != null) {
                    // Store ref token - login ke baad use hoga
                    getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                        .putString("pending_ref_token", refToken)
                        .apply()
                }
                return
            }

            // ytsubexchange://auth?token=...&ref=...
            val token = uri.getQueryParameter("token")
            val ref = uri.getQueryParameter("ref")
            val newUser = uri.getQueryParameter("newUser")

            if (token != null) {
                getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                    .putString("token", token)
                    .remove("pending_ref_token") // clear after login
                    .apply()
                tokenState?.value = token

                val referred = uri.getQueryParameter("referred")
                val coins = uri.getQueryParameter("coins")

                if (referred == "true") {
                    // Referral link se aaya - refCode pre-fill karke popup dikhao
                    val refCode = uri.getQueryParameter("refCode") ?: ""
                    getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                        .putString("pending_ref", if (refCode.isNotEmpty()) refCode else "ASK")
                        .putString("pending_ref_coins", coins ?: "20")
                        .putBoolean("pending_ref_earned", true) // coins already mil gaye
                        .apply()
                } else if (uri.getQueryParameter("newUser") == "true") {
                    // Organic user - popup dikhao code enter karne ke liye
                    getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                        .putString("pending_ref", "ASK")
                        .apply()
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    currentScreen: Screen,
    onSelect: (Screen) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(Screen.Home, "\uD83C\uDFE0", "Home"),
        Triple(Screen.Chat, "\uD83D\uDCAC", "Chat"),
        Triple(Screen.Referral, "\uD83C\uDF81", "Refer"),
        Triple(Screen.Exchange, "\uD83D\uDC41", "Exchange"),
        Triple(Screen.Profile, "\uD83D\uDC64", "Profile"),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ambientColor = Color(0xFFFF0000).copy(alpha = 0.3f),
                spotColor = Color(0xFFFF0000).copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                if (dark)
                    Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF111111)))
                else
                    Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF5F5F5)))
            )
            .height(68.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, emoji, label) ->
                val selected = currentScreen == screen
                val bgAlpha by animateColorAsState(
                    targetValue = if (selected) Color(0xFFFF0000).copy(alpha = 0.15f) else Color.Transparent,
                    animationSpec = tween(300), label = ""
                )
                val labelColor by animateColorAsState(
                    targetValue = if (selected) Color(0xFFFF0000) else if (dark) Color(0xFF666666) else Color(0xFFAAAAAA),
                    animationSpec = tween(300), label = ""
                )
                val emojiSize by animateDpAsState(
                    targetValue = if (selected) 22.dp else 18.dp,
                    animationSpec = tween(300), label = ""
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgAlpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(emoji, fontSize = emojiSize.value.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 10.sp,
                        color = labelColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(3.dp))
                    // Active dot indicator
                    Box(
                        modifier = Modifier
                            .size(if (selected) 5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF0000))
                    )
                }
            }
        }
    }
}


