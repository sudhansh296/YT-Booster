package com.ytsubexchange

import androidx.core.app.NotificationCompat

import android.app.PendingIntent

import android.app.NotificationManager

import android.app.NotificationChannel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.ytsubexchange.ui.*
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.ChatViewModel
import com.ytsubexchange.viewmodel.GroupVoiceChatViewModel
import com.ytsubexchange.viewmodel.MainViewModel

sealed class Screen {
    object Home : Screen()
    object Leaderboard : Screen()
    object Referral : Screen()
    object History : Screen()
    object Profile : Screen()
    object Chat : Screen()
    data class ChatWithCommunity(val openCommunity: Boolean = false) : Screen()
    object Exchange : Screen()
    // Sub-screens (no bottom nav)
    object Streak : Screen()
    object Settings : Screen()
    object Transactions : Screen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private val callViewModel: com.ytsubexchange.viewmodel.CallViewModel by viewModels()
    private val groupVoiceChatViewModel: com.ytsubexchange.viewmodel.GroupVoiceChatViewModel by viewModels()
    private var tokenState: MutableState<String?>? = null
    var pendingCallAccept: (() -> Unit)? = null

    // File picker launcher
    private var filePickerCallback: ((android.net.Uri, String, String) -> Unit)? = null
    private val filePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { u ->
            val mimeType = contentResolver.getType(u) ?: "application/octet-stream"
            val displayName = contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "file"
            } ?: "file"
            filePickerCallback?.invoke(u, mimeType, displayName)
            filePickerCallback = null
        }
    }

    fun pickFile(mimeType: String = "*/*", callback: (android.net.Uri, String, String) -> Unit) {
        filePickerCallback = callback
        filePickerLauncher.launch(mimeType)
    }

    // Screen capture launcher
    private val screenCaptureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            callViewModel.screenCaptureResultCode = result.resultCode
            callViewModel.screenCaptureData = result.data
            // Start foreground service before screen capture (Android 14+ requirement)
            ScreenCaptureService.start(this)
            // Small delay to let foreground service start before MediaProjection
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                callViewModel.startScreenShare()
            }, 300)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all {
                it == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                pendingCallAccept?.invoke()
                pendingCallAccept = null
            } else {
                callViewModel.rejectCall()
                pendingCallAccept = null
            }
        }
    }

    fun requestScreenCapture() {
        val mgr = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    // Voice chat screen capture
    private val voiceChatScreenCaptureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(this)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                groupVoiceChatViewModel.startScreenShare(result.resultCode, result.data!!)
            }, 300)
        }
    }

    fun requestVoiceChatScreenCapture() {
        val mgr = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        voiceChatScreenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleIntent(intent)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        isDarkTheme.value = prefs.getBoolean("dark_theme", true)

        // Handle offline messages and missed calls when app opens
        handleOfflineData()

        // Setup Coil with trust-all SSL (self-signed cert support)
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
        val coilOkHttp = okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        val imageLoader = coil.ImageLoader.Builder(this)
            .okHttpClient(coilOkHttp)
            .build()
        coil.Coil.setImageLoader(imageLoader)

        // Request media permissions on startup (Android 13+)
        requestMediaPermissions()

        // Check for app update in background
        checkForUpdate()
        // Register FCM token
        registerFcmToken()
        // Network reconnect — net wapas aane pe auto-reload
        setupNetworkCallback()

        setContent {
            val authPrefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val token = remember { mutableStateOf(authPrefs.getString("token", null)) }
            tokenState = token
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

            if (token.value != null) {
                LaunchedEffect(token.value) {
                    viewModel.init(token.value!!)
                    // Handle pending group invite
                    val authPrefs3 = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    val pendingGroupInvite = authPrefs3.getString("pending_group_invite", null)
                    if (pendingGroupInvite != null) {
                        authPrefs3.edit().remove("pending_group_invite").apply()
                        chatViewModel.joinGroupByLink(pendingGroupInvite)
                    }
                }

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
                        BackHandler { currentScreen = Screen.Profile }
                        StreakScreen(viewModel) { currentScreen = Screen.Profile }
                        return@setContent
                    }
                    is Screen.Settings -> {
                        BackHandler { currentScreen = Screen.Profile }
                        SettingsScreen { currentScreen = Screen.Profile }
                        return@setContent
                    }
                    is Screen.Transactions -> {
                        BackHandler { currentScreen = Screen.Profile }
                        TransactionScreen(viewModel, onBack = { currentScreen = Screen.Profile })
                        return@setContent
                    }
                    else -> {}
                }

                // Main screens with bottom nav
                val dark by isDarkTheme

                // Back press on non-Home screens → go to Home; on Home → minimize app
                // Chat screen pe: agar room open hai toh room close karo, warna Home pe jao
                BackHandler(enabled = currentScreen != Screen.Home) {
                    val isOnChat = currentScreen is Screen.Chat || currentScreen is Screen.ChatWithCommunity
                    if (isOnChat && chatViewModel.openRoom.value != null) {
                        chatViewModel.closeRoom()
                    } else {
                        currentScreen = Screen.Home
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(AppColors.bg(dark)).statusBarsPadding()) {
                    val openRoom by chatViewModel.openRoom.collectAsState()
                    val isOnChatScreen = currentScreen is Screen.Chat || currentScreen is Screen.ChatWithCommunity
                    val isInChatWindow = isOnChatScreen && openRoom != null

                    // Keyboard state detect karo
                    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

                    // Keyboard open hone pe bottom padding 0 karo (navbar space hide)
                    val bottomPad = when {
                        isInChatWindow -> 0.dp
                        imeVisible -> 0.dp
                        else -> 68.dp
                    }


                    Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomPad).imePadding()) {
                        when (currentScreen) {
                            is Screen.Home -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToCommunity = { currentScreen = Screen.ChatWithCommunity(true) }
                            )
                            is Screen.Leaderboard -> LeaderboardScreen(viewModel)
                            is Screen.Referral -> ReferralScreen(viewModel)
                            is Screen.History -> TransactionScreen(viewModel)
                            is Screen.Chat -> ChatScreen(chatViewModel, onBack = { currentScreen = Screen.Home }, callViewModel = callViewModel, voiceChatViewModel = groupVoiceChatViewModel, onPickFile = { mime, cb -> pickFile(mime, cb) }, onRequestScreenCapture = { requestVoiceChatScreenCapture() })
                            is Screen.ChatWithCommunity -> ChatScreen(chatViewModel, onBack = { currentScreen = Screen.Home }, openCommunity = (currentScreen as Screen.ChatWithCommunity).openCommunity, callViewModel = callViewModel, voiceChatViewModel = groupVoiceChatViewModel, onPickFile = { mime, cb -> pickFile(mime, cb) }, onRequestScreenCapture = { requestVoiceChatScreenCapture() })
                            is Screen.Exchange -> ExchangeScreen(viewModel)
                            is Screen.Profile -> ProfileScreen(
                                viewModel = viewModel,
                                onNavigateToStreak = { currentScreen = Screen.Streak },
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onNavigateToTransactions = { currentScreen = Screen.Transactions }
                            )
                            else -> {}
                        }
                    }

                    // Bottom Navigation Bar — chat window ya keyboard open hone pe hide karo
                    val hideNavForKeyboard = imeVisible && (
                        currentScreen is Screen.ChatWithCommunity ||
                        currentScreen is Screen.Chat
                    )
                    if (!isInChatWindow && !hideNavForKeyboard) {
                        BottomNavBar(
                            currentScreen = currentScreen,
                            onSelect = { currentScreen = it },
                            dark = dark,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    // In-app chat notification — compact WhatsApp style popup
                    val inAppNotif by chatViewModel.inAppNotif.collectAsState()
                    var showNotif by remember { mutableStateOf(false) }
                    var notifOffsetY by remember { mutableStateOf(0f) }
                    LaunchedEffect(inAppNotif) {
                        if (inAppNotif != null) {
                            notifOffsetY = 0f
                            showNotif = true
                            delay(3000)
                            showNotif = false
                            chatViewModel.clearInAppNotif()
                        }
                    }
                    AnimatedVisibility(
                        visible = showNotif && inAppNotif != null,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .offset { IntOffset(0, notifOffsetY.roundToInt()) }
                    ) {
                        inAppNotif?.let { notif ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1E1E2E).copy(alpha = 0.97f))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                if (notifOffsetY < -40f) {
                                                    showNotif = false
                                                    chatViewModel.clearInAppNotif()
                                                } else {
                                                    notifOffsetY = 0f
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                notifOffsetY = (notifOffsetY + dragAmount.y).coerceAtMost(20f)
                                            }
                                        )
                                    }
                                    .clickable {
                                        showNotif = false
                                        chatViewModel.clearInAppNotif()
                                        chatViewModel.openRoom(notif.room)
                                        currentScreen = Screen.Chat
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp, 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Avatar
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF6B6B)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            notif.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "💬",
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(notif.senderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                        Text(notif.text, color = Color(0xFFBBBBBB), fontSize = 12.sp, maxLines = 1)
                                    }
                                    // Dismiss X
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF2A2A3E))
                                            .clickable { showNotif = false; chatViewModel.clearInAppNotif() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // ── Incoming Chat Request Dialog (WhatsApp DM request style) ──
                    val pendingRequests by chatViewModel.pendingRequests.collectAsState()
                    val latestRequest = pendingRequests.firstOrNull()
                    var shownRequestId by remember { mutableStateOf<String?>(null) }
                    // Naya request aaya toh show karo
                    LaunchedEffect(latestRequest?.requestId) {
                        if (latestRequest != null && latestRequest.requestId != shownRequestId) {
                            shownRequestId = latestRequest.requestId
                        }
                    }
                    val showChatReqDialog = latestRequest != null && latestRequest.requestId == shownRequestId
                    if (showChatReqDialog && latestRequest != null) {
                        com.ytsubexchange.ui.ChatRequestDialog(
                            request = latestRequest,
                            onAccept = {
                                chatViewModel.acceptChatRequest(latestRequest.requestId)
                                shownRequestId = null
                                currentScreen = Screen.Chat
                            },
                            onReject = {
                                chatViewModel.rejectChatRequest(latestRequest.requestId)
                                shownRequestId = null
                            },
                            onDismiss = { shownRequestId = null }
                        )
                    }

                    // Group Invite Dialog
                    val groupInvite by chatViewModel.groupInvite.collectAsState()
                    groupInvite?.let { invite ->
                        androidx.compose.ui.window.Dialog(onDismissRequest = { chatViewModel.rejectGroupInvite(invite.roomId) }) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF1A1A2E))
                                    .padding(20.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("👥", fontSize = 40.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Group Invite", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${invite.invitedBy} ne aapko '${invite.roomName}' group mein add kiya hai",
                                        color = Color(0xFFBBBBBB), fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF2A1A1A))
                                                .clickable { chatViewModel.rejectGroupInvite(invite.roomId) }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Reject ✕", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Box(
                                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                                .background(Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFFF6B6B))))
                                                .clickable {
                                                    chatViewModel.acceptGroupInvite(invite.roomId)
                                                    currentScreen = Screen.Chat
                                                }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Join ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Incoming Call Dialog
                    val callState by callViewModel.callState.collectAsState()
                    if (callState == com.ytsubexchange.viewmodel.CallState.INCOMING) {
                        IncomingCallDialog(
                            viewModel = callViewModel,
                            onDismiss = {},
                            onAcceptWithPermission = { onGranted ->
                                val callType = callViewModel.callType.value
                                val neededPerms = if (callType == "video") {
                                    arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)
                                } else {
                                    arrayOf(android.Manifest.permission.RECORD_AUDIO)
                                }
                                val allGranted = neededPerms.all {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        this@MainActivity, it
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (allGranted) {
                                    onGranted()
                                } else {
                                    androidx.core.app.ActivityCompat.requestPermissions(
                                        this@MainActivity, neededPerms, 101
                                    )
                                    pendingCallAccept = onGranted
                                }
                            }
                        )
                    }

                    // Active Call Screen (full screen overlay)
                    if (callState == com.ytsubexchange.viewmodel.CallState.CALLING ||
                        callState == com.ytsubexchange.viewmodel.CallState.IN_CALL) {
                        Box(Modifier.fillMaxSize()) {
                            ActiveCallScreen(
                                viewModel = callViewModel,
                                onEnd = {},
                                onRequestScreenCapture = { requestScreenCapture() }
                            )
                        }
                    }

                    // Floating Chatbot Button
                    FloatingChatbotButton()
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

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun setupNetworkCallback() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                // Net wapas aaya — data reload + socket reconnect
                val savedToken = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", null) ?: return
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    viewModel.init(savedToken)
                    chatViewModel.loadRooms()
                    // Socket reconnect
                    com.ytsubexchange.network.SocketManager.reconnect()
                    android.util.Log.d("Network", "Net wapas aaya — auto reload + socket reconnect")
                }, 500) // thoda wait karo net stable hone ke liye
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(it)
        }
    }

    // PiP - enter when home button pressed during call/voice chat
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val callState = callViewModel.callState.value
        val voiceChatActive = groupVoiceChatViewModel.isActive.value
        if ((callState == com.ytsubexchange.viewmodel.CallState.IN_CALL ||
             callState == com.ytsubexchange.viewmodel.CallState.CALLING ||
             voiceChatActive) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                android.util.Log.e("PiP", "enterPiP failed: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        // UI will adapt automatically via Compose state
    }

    private fun requestMediaPermissions() {
        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.RECORD_AUDIO
            )
        } else {
            // Android 12 and below
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO
            )
        }
        val notGranted = perms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 200)
        }
    }

    private fun registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val prefs = getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("fcm_token", token).apply()
                val authToken = prefs.getString("token", null) ?: return@addOnSuccessListener
                Thread {
                    try {
                        com.ytsubexchange.network.RetrofitClient.api
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
                val versionInfo = com.ytsubexchange.network.RetrofitClient.api.checkVersion().execute().body()
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

            // https://api.picrypto.in/join-group/TOKEN - group invite link
            if (scheme == "https" && host == "api.picrypto.in") {
                val path = uri.path ?: ""
                if (path.startsWith("/join-group/")) {
                    val inviteToken = path.removePrefix("/join-group/").trim()
                    if (inviteToken.isNotEmpty()) {
                        val authToken = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", null)
                        if (authToken != null) {
                            chatViewModel.joinGroupByLink(inviteToken)
                        } else {
                            getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                                .putString("pending_group_invite", inviteToken)
                                .apply()
                        }
                    }
                    return
                }
            }

            // ytsubexchange://join-group/TOKEN - from landing page "Open in App" button
            if (scheme == "ytsubexchange" && host == "join-group") {
                val inviteToken = uri.pathSegments.firstOrNull() ?: uri.lastPathSegment ?: ""
                if (inviteToken.isNotEmpty()) {
                    val authToken = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", null)
                    if (authToken != null) {
                        chatViewModel.joinGroupByLink(inviteToken)
                    } else {
                        getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                            .putString("pending_group_invite", inviteToken)
                            .apply()
                    }
                }
                return
            }

            // ytsubexchange://auth?token=...
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

    private fun handleOfflineData() {
        // Process offline messages
        val msgPrefs = getSharedPreferences("offline_messages", Context.MODE_PRIVATE)
        val offlineMessages = msgPrefs.getStringSet("messages", emptySet()) ?: emptySet()
        
        if (offlineMessages.isNotEmpty()) {
            // Show notification about offline messages
            val count = offlineMessages.size
            showOfflineDataNotification("📬 $count new messages", "Tap to view offline messages")
            
            // Clear processed messages
            msgPrefs.edit().remove("messages").apply()
        }

        // Process missed calls
        val callPrefs = getSharedPreferences("missed_calls", Context.MODE_PRIVATE)
        val missedCalls = callPrefs.getStringSet("calls", emptySet()) ?: emptySet()
        
        if (missedCalls.isNotEmpty()) {
            val count = missedCalls.size
            showOfflineDataNotification("📞 $count missed calls", "Tap to view call history")
            
            // Clear processed calls
            callPrefs.edit().remove("calls").apply()
        }
    }

    private fun showOfflineDataNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ytbooster_offline"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Offline Data", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(3000, notification)
    }
}

private data class BotMsg(val text: String, val isUser: Boolean)

private fun ytBuddyReply(msg: String): String {
    val m = msg.lowercase().trim()
    return when {
        m.matches(Regex("(hi|hello|hey|namaste|hii|helo|hlo|sup|yo|kya haal|kaise ho|gm|good morning|good evening).*")) ->
            listOf(
                "Namaste! 😊 Kaise ho? Main YT Buddy hoon — kuch bhi pucho!",
                "Hey! 👋 Kya haal hai? Coins, subscribers, ya kuch aur?",
                "Hello! 🤖 Kya help chahiye aaj?"
            ).random()
        m.contains("coin") || m.contains("paise") || m.contains("earn") || m.contains("kamao") ->
            "Coins earn karne ke 3 tarike:\n1️⃣ Exchange tab mein subscribe karo\n2️⃣ Roz daily bonus claim karo\n3️⃣ Friends refer karo — 20 coins each! 💰"
        m.contains("subscriber") || m.contains("subscribe") || m.contains("sub ") ->
            "Subscribers kharidne ke liye:\n📱 Home screen → 'Buy Subscribers'\n💰 Coins se subscribers milenge!\n\nPehle coins earn karo, phir subscribers lo. 📈"
        m.contains("refer") || m.contains("referral") || m.contains("code") || m.contains("invite") ->
            "Referral system:\n🎁 Refer tab mein apna code milega\n💰 Dost join kare to 20 coins milenge\n📊 Stats bhi dekh sakte ho!"
        m.contains("streak") || m.contains("daily") || m.contains("bonus") || m.contains("roz") ->
            "Daily streak ke liye roz app open karo aur bonus claim karo! 🔥\nLonger streak = more bonus coins. Miss mat karna!"
        m.contains("youtube") || m.contains("yt ") || m.contains("video") || m.contains("channel") || m.contains("thumbnail") ->
            "YouTube tips:\n📌 Consistent upload schedule rakho\n🎯 Catchy thumbnail + title banao\n💬 Comments ka reply karo\n📊 Analytics dekho aur improve karo 🎬"
        m.contains("help") || m.contains("kya kar") || m.contains("kaise") || m.contains("batao") ->
            "Main in topics pe help kar sakta hoon:\n💰 Coins earn karna\n📈 Subscribers lena\n🎁 Referral system\n🔥 Daily streak\n🎬 YouTube tips\n\nKya jaanna chahte ho?"
        m.contains("thank") || m.contains("shukriya") || m.contains("thx") || m.contains("ty") ->
            listOf("Koi baat nahi! 😊", "Welcome! 🙌", "Khushi hui help karke! 🤖✨").random()
        m.contains("app") || m.contains("feature") || m.contains("kya hai") ->
            "YT Booster ek YouTube subscriber exchange platform hai!\n✅ Doosron ko subscribe karo → coins milenge\n✅ Coins se apne channel ke subscribers lo\n✅ Community chat, groups, aur bahut kuch! 🚀"
        m.contains("problem") || m.contains("issue") || m.contains("error") || m.contains("nahi") ->
            "Koi problem hai? 🤔 Thoda detail mein batao — main help karne ki koshish karunga!\n\nYa phir app restart karke try karo. 😊"
        else ->
            listOf(
                "Interesting! 🤔 Coins, subscribers, referral ya YouTube tips ke baare mein kuch specific pucho!",
                "Samajh gaya! App se related kuch bhi pucho — main bataunga. 😊",
                "Acha sawaal! Main YT Booster ka assistant hoon. App ke baare mein kuch bhi pucho! 🤖"
            ).random()
    }
}

@Composable
fun FloatingChatbotButton() {
    var showChat by remember { mutableStateOf(false) }

    var messages by remember { mutableStateOf(listOf(BotMsg("Namaste! Main YT Buddy hoon 🤖\nKoi bhi sawaal pucho — YouTube tips, coins, ya kuch bhi!", false))) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var queryCounter by remember { mutableStateOf(0) }
    var lastQuery by remember { mutableStateOf("") }

    LaunchedEffect(queryCounter) {
        if (queryCounter == 0 || lastQuery.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
            val token = "Bearer ${prefs.getString("token", "")}"
            val history = messages.dropLast(1).takeLast(8).map {
                mapOf("role" to if (it.isUser) "user" else "ai", "text" to it.text)
            }
            val resp = com.ytsubexchange.network.RetrofitClient.api.aiChat(
                token, mapOf("message" to lastQuery, "history" to history)
            )
            messages = messages + BotMsg(resp.reply, false)
        } catch (e: Exception) {
            // Fallback to local AI if server fails
            messages = messages + BotMsg(ytBuddyReply(lastQuery), false)
        } finally {
            isLoading = false
        }
    }

    // Drag position
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }
    var parentWidth by remember { mutableStateOf(0) }
    var parentHeight by remember { mutableStateOf(0) }
    val btnSizePx = with(LocalDensity.current) { 58.dp.toPx() }
    val bottomNavPx = with(LocalDensity.current) { 90.dp.toPx() }
    val marginPx = with(LocalDensity.current) { 16.dp.toPx() }

    Box(
        modifier = Modifier.fillMaxSize().navigationBarsPadding()
            .onGloballyPositioned { coords ->
                if (!initialized) {
                    parentWidth = coords.size.width
                    parentHeight = coords.size.height
                    offsetX = parentWidth - btnSizePx - marginPx
                    offsetY = parentHeight - btnSizePx - bottomNavPx - marginPx
                    initialized = true
                }
            }
    ) {
        if (initialized) {
            // Floating button — gradient + pulse ring
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(58.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935))))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, parentWidth - btnSizePx)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, parentHeight - btnSizePx)
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        showChat = !showChat
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🤖", fontSize = 26.sp)
            }
        }
    }

    if (showChat) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showChat = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .systemBarsPadding()
                    .imePadding()
                    .heightIn(min = 300.dp, max = 520.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0D0D1A))
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Header
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935))))
                            .padding(16.dp, 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) { Text("🤖", fontSize = 20.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("YT Buddy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("AI Assistant • Online", color = Color.White.copy(0.75f), fontSize = 11.sp)
                            }
                            Box(
                                Modifier.size(28.dp).clip(CircleShape)
                                    .background(Color.White.copy(0.15f))
                                    .clickable { showChat = false },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", color = Color.White, fontSize = 13.sp) }
                        }
                    }

                    // Messages
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages.size) { i ->
                            val msg = messages[i]
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (!msg.isUser) {
                                    Box(
                                        Modifier.size(28.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935)))),
                                        contentAlignment = Alignment.Center
                                    ) { Text("🤖", fontSize = 14.sp) }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Box(
                                    Modifier.widthIn(max = 240.dp)
                                        .clip(RoundedCornerShape(
                                            topStart = 16.dp, topEnd = 16.dp,
                                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                        ))
                                        .background(
                                            if (msg.isUser)
                                                Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF6B6B)))
                                            else
                                                Brush.linearGradient(listOf(Color(0xFF1A1A3E), Color(0xFF16213E)))
                                        )
                                        .padding(12.dp, 8.dp)
                                ) {
                                    Text(msg.text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                        if (isLoading) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(28.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935)))),
                                        contentAlignment = Alignment.Center
                                    ) { Text("🤖", fontSize = 14.sp) }
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1A1A3E))
                                            .padding(14.dp, 10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF7B2FF7), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }

                    // Input row
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF0A0A14)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1A1A2E)).padding(14.dp, 10.dp)
                        ) {
                            if (input.isEmpty()) Text("Kuch bhi pucho...", color = Color(0xFF666666), fontSize = 13.sp)
                            androidx.compose.foundation.text.BasicTextField(
                                value = input, onValueChange = { input = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                                maxLines = 3, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(
                                    if (input.isNotBlank() && !isLoading)
                                        Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE53935)))
                                    else
                                        Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFF333333)))
                                )
                                .clickable {
                                    if (input.isNotBlank() && !isLoading) {
                                        val userText = input.trim()
                                        input = ""
                                        messages = messages + BotMsg(userText, true)
                                        lastQuery = userText
                                        queryCounter++
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("➤", color = Color.White, fontSize = 18.sp)
                        }
                    }
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
        Triple(Screen.Home, "🏠", "Home"),
        Triple(Screen.Chat, "💬", "Chat"),
        Triple(Screen.Referral, "🎁", "Refer"),
        Triple(Screen.Exchange, "🎬", "Exchange"),
        Triple(Screen.Profile, "👤", "Profile"),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
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
                val selected = currentScreen == screen ||
                    (screen == Screen.Chat && currentScreen is Screen.ChatWithCommunity)
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
