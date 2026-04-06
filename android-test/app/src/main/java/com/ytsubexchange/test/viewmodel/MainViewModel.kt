package com.ytsubexchange.test.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytsubexchange.test.data.*
import com.ytsubexchange.test.network.RetrofitClient
import com.ytsubexchange.test.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class QueueState {
    object Idle : QueueState()
    data class Waiting(val queueSize: Int = 0) : QueueState()
    data class InQueue(val channels: List<MatchData>, val queueSize: Int = 0) : QueueState()
    data class Matched(val match: MatchData, val partnerConfirmed: Boolean = false) : QueueState()
    data class CoinEarned(val totalCoins: Int, val earned: Int = 1) : QueueState()
    object VerifyFailed : QueueState()
}

class MainViewModel : ViewModel() {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _queueState = MutableStateFlow<QueueState>(QueueState.Idle)
    val queueState: StateFlow<QueueState> = _queueState

    private val _notices = MutableStateFlow<List<NoticeData>>(emptyList())
    val notices: StateFlow<List<NoticeData>> = _notices

    private val _transactions = MutableStateFlow<List<TransactionData>>(emptyList())
    val transactions: StateFlow<List<TransactionData>> = _transactions

    private val _leaderboard = MutableStateFlow<LeaderboardResponse?>(null)
    val leaderboard: StateFlow<LeaderboardResponse?> = _leaderboard

    private val _referral = MutableStateFlow<ReferralResponse?>(null)
    val referral: StateFlow<ReferralResponse?> = _referral

    private val _streak = MutableStateFlow<StreakResponse?>(null)
    val streak: StateFlow<StreakResponse?> = _streak

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private val _updateInfo = MutableStateFlow<com.ytsubexchange.test.data.VersionResponse?>(null)
    val updateInfo: StateFlow<com.ytsubexchange.test.data.VersionResponse?> = _updateInfo

    fun setUpdateAvailable(info: com.ytsubexchange.test.data.VersionResponse) {
        _updateInfo.value = info
    }

    private var token: String = ""

    fun init(savedToken: String) {
        token = savedToken
        setupSocketListeners()
        SocketManager.connect()
        loadInit()
        loadNotices()
        loadTransactions()
        loadLeaderboard()
    }

    fun loadInit() {
        _loadError.value = null
        viewModelScope.launch {
            try {
                val data = RetrofitClient.api.getInit("Bearer $token")
                _profile.value = data.profile
                _streak.value = data.streak
                _referral.value = data.referral
            } catch (e: Exception) {
                // fallback to individual profile call
                try {
                    _profile.value = RetrofitClient.api.getProfile("Bearer $token")
                } catch (e2: Exception) {
                    _loadError.value = parseError(e2)
                }
            }
        }
    }

    fun loadNotices() {
        viewModelScope.launch {
            try {
                _notices.value = RetrofitClient.api.getNotices("Bearer $token")
            } catch (e: Exception) {
                android.util.Log.e("Notices", parseError(e))
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = RetrofitClient.api.getProfile("Bearer $token")
            } catch (e: Exception) {
                android.util.Log.e("Profile", parseError(e))
            }
        }
    }

    fun loadTransactions() {
        viewModelScope.launch {
            try {
                _transactions.value = RetrofitClient.api.getTransactions("Bearer $token")
            } catch (e: Exception) {
                android.util.Log.e("Transactions", parseError(e))
            }
        }
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            try {
                _leaderboard.value = RetrofitClient.api.getLeaderboard("Bearer $token")
            } catch (e: Exception) {
                android.util.Log.e("Leaderboard", parseError(e))
            }
        }
    }

    fun loadReferral() {
        viewModelScope.launch {
            try {
                _referral.value = RetrofitClient.api.getReferral("Bearer $token")
            } catch (e: Exception) {
                android.util.Log.e("Referral", parseError(e))
            }
        }
    }

    fun loadStreak() {
        viewModelScope.launch {
            try {
                _streak.value = RetrofitClient.api.getStreak("Bearer $token")
            } catch (e: Exception) {
                android.util.Log.e("Streak", parseError(e))
            }
        }
    }

    fun applyReferralCode(code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.applyReferral("Bearer $token", ReferralApplyRequest(code))
                if (res.success) { loadProfile(); loadReferral() }
                onResult(res.success, res.message)
            } catch (e: Exception) {
                onResult(false, parseError(e))
            }
        }
    }

    fun claimDailyBonus(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.claimDailyBonus("Bearer $token")
                if (res.success) { loadProfile(); loadStreak() }
                onResult(res.success, res.message)
            } catch (e: Exception) {
                onResult(false, parseError(e))
            }
        }
    }

    fun buySubscribers(coins: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.buySubscribers("Bearer $token", BuySubsRequest(coins))
                if (res.success) loadProfile()
                onResult(res.success, res.message ?: "Order place ho gaya")
            } catch (e: Exception) {
                onResult(false, parseError(e))
            }
        }
    }

    fun submitCoinRequest(coins: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.submitCoinRequest("Bearer $token", CoinRequestBody(coins))
            } catch (e: Exception) {
                android.util.Log.e("CoinRequest", parseError(e))
            }
        }
    }

    fun submitWatchReward(coins: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.claimWatchReward("Bearer $token", mapOf("coins" to coins))
                if (res.success) loadProfile()
                onResult(res.success, if (res.success) "+$coins coins mila!" else res.message)
            } catch (e: Exception) {
                // Offline fallback — locally add coins for testing
                _profile.value = _profile.value?.copy(coins = (_profile.value?.coins ?: 0) + coins)
                onResult(true, "+$coins coins mila!")
            }
        }
    }

    fun submitVideoOrder(url: String, cost: Int, type: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.submitVideoOrder("Bearer $token",
                    mapOf("url" to url, "cost" to cost.toString(), "type" to type))
                if (res.success) loadProfile()
                onResult(res.success, res.message ?: "Video submit ho gaya!")
            } catch (e: Exception) {
                // Offline fallback — deduct coins locally for testing
                val cur = _profile.value?.coins ?: 0
                if (cur >= cost) {
                    _profile.value = _profile.value?.copy(coins = cur - cost)
                    onResult(true, "Video submit ho gaya! (test mode)")
                } else {
                    onResult(false, "Coins kam hain")
                }
            }
        }
    }

    // Helper: HTTP error body se user-friendly message nikalo
    private fun parseError(e: Exception): String {
        return try {
            if (e is retrofit2.HttpException) {
                val body = e.response()?.errorBody()?.string()
                if (!body.isNullOrEmpty()) {
                    val json = org.json.JSONObject(body)
                    json.optString("error", json.optString("message", "")).ifEmpty { friendlyHttpError(e.code()) }
                } else friendlyHttpError(e.code())
            } else if (e.message?.contains("Unable to resolve host") == true ||
                       e.message?.contains("timeout") == true ||
                       e.message?.contains("connect") == true) {
                "Internet connection check karo"
            } else {
                "Kuch galat hua, dobara try karo"
            }
        } catch (ex: Exception) {
            "Kuch galat hua, dobara try karo"
        }
    }

    private fun friendlyHttpError(code: Int) = when (code) {
        400 -> "Request galat hai, dobara try karo"
        401 -> "Session expire ho gaya, dobara login karo"
        403 -> "Ye kaam karne ki permission nahi hai"
        404 -> "Ye cheez nahi mili"
        429 -> "Bahut zyada requests, thodi der baad try karo"
        500, 502, 503 -> "Server mein problem hai, thodi der baad try karo"
        else -> "Kuch galat hua (error $code)"
    }

    fun joinQueue() {
        _queueState.value = QueueState.Waiting(0)
        SocketManager.joinQueue(token)
    }

    fun leaveQueue() {
        _queueState.value = QueueState.Idle
        SocketManager.leaveQueue()
    }

    fun confirmSubscribe(matchId: String) {
        SocketManager.confirmSubscribe(token, matchId)
    }

    private fun setupSocketListeners() {
        SocketManager.on("waiting") { args ->
            val data = args[0] as JSONObject
            _queueState.value = QueueState.Waiting(data.optInt("queueSize", 0))
        }
        SocketManager.on("queue_update") { args ->
            val data = args[0] as JSONObject
            val current = _queueState.value
            if (current is QueueState.Waiting) {
                _queueState.value = QueueState.Waiting(data.optInt("queueSize", 0))
            }
        }
        SocketManager.on("queue_list") { args ->
            val data = args[0] as JSONObject
            val arr = data.getJSONArray("channels")
            val list = mutableListOf<MatchData>()
            for (i in 0 until arr.length()) {
                val ch = arr.getJSONObject(i)
                list.add(MatchData(
                    channelId = ch.getString("channelId"),
                    channelName = ch.getString("channelName"),
                    channelUrl = ch.getString("channelUrl"),
                    profilePic = ch.getString("profilePic"),
                    matchId = ch.getString("matchId"),
                    coinsReward = ch.optInt("coinsReward", 1)
                ))
            }
            _queueState.value = QueueState.InQueue(list, data.optInt("queueSize", 0))
        }
        SocketManager.on("match_found") { args ->
            val data = args[0] as JSONObject
            _queueState.value = QueueState.Matched(
                MatchData(
                    channelId = data.getString("channelId"),
                    channelName = data.getString("channelName"),
                    channelUrl = data.getString("channelUrl"),
                    profilePic = data.getString("profilePic"),
                    matchId = data.getString("matchId"),
                    coinsReward = data.optInt("coinsReward", 1)
                )
            )
        }
        SocketManager.on("partner_confirmed") { args ->
            val current = _queueState.value
            if (current is QueueState.Matched) {
                _queueState.value = current.copy(partnerConfirmed = true)
            }
        }
        SocketManager.on("coins_earned") { args ->
            val data = args[0] as JSONObject
            _queueState.value = QueueState.CoinEarned(
                totalCoins = data.getInt("coins"),
                earned = data.optInt("earned", 1)
            )
            loadProfile()
        }
        SocketManager.on("verify_failed") {
            _queueState.value = QueueState.VerifyFailed
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocketManager.disconnect()
    }
}
