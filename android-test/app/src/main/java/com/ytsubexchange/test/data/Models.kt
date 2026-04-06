package com.ytsubexchange.test.data

data class UserProfile(
    val channelName: String,
    val profilePic: String,
    val channelUrl: String,
    val coins: Int,
    val totalEarned: Int,
    val subscribersGiven: Int,
    val subscribersReceived: Int
)

data class MatchData(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val profilePic: String,
    val matchId: String,
    val coinsReward: Int = 1
)

data class BuySubsRequest(val coins: Int)
data class BuySubsResponse(val success: Boolean, val remainingCoins: Int, val message: String? = null)
data class CoinsEarned(val coins: Int, val earned: Int)
data class CoinRequestBody(val coins: Int)
data class CoinRequestResponse(val success: Boolean, val requestId: String)
data class NoticeData(val _id: String, val title: String, val message: String)
data class DailyBonusResponse(val success: Boolean, val coins: Int = 0, val message: String = "", val streak: Int = 0)

data class TransactionData(
    val _id: String,
    val type: String,
    val coins: Int,
    val description: String,
    val createdAt: String
)

data class LeaderboardEntry(
    val _id: String,
    val channelName: String,
    val profilePic: String,
    val totalEarned: Int,
    val subscribersGiven: Int,
    val subscribersReceived: Int
)

data class LeaderboardResponse(
    val leaderboard: List<LeaderboardEntry>,
    val myRank: Int,
    val myEarned: Int
)

data class ReferralStats(
    val today: Int = 0,
    val thisWeek: Int = 0,
    val thisMonth: Int = 0,
    val total: Int = 0
)

data class ReferralMilestone(
    val count: Int,
    val bonus: Int,
    val claimed: Boolean = false,
    val reached: Boolean = false
)

data class ReferralResponse(
    val referralCode: String,
    val referralLink: String = "",
    val referralCount: Int,
    val referralEarned: Int,
    val alreadyReferred: Boolean = false,
    val adminCodeUsed: String? = null,
    val stats: ReferralStats = ReferralStats(),
    val milestones: List<ReferralMilestone> = emptyList()
)

data class ReferralApplyRequest(val code: String)
data class ReferralApplyResponse(val success: Boolean, val message: String = "")

data class StreakResponse(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastDailyBonus: String?
)

data class InitResponse(
    val profile: UserProfile,
    val streak: StreakResponse,
    val referral: ReferralResponse
)

data class VersionResponse(
    val latestVersion: Int,
    val versionName: String,
    val forceUpdate: Boolean = false,
    val downloadUrl: String,
    val changelog: String = ""
)

// ── Chat Models ──────────────────────────────────────────────
data class ReplyRef(
    val msgId: String,
    val text: String,
    val senderName: String
)

data class ChatMessage(
    val _id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPic: String = "",
    val text: String = "",
    val createdAt: String = "",
    val read: Boolean = false,
    val pinned: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val replyTo: ReplyRef? = null
)

data class ChatRoom(
    val _id: String,
    val name: String,
    val pic: String = "",
    val isGroup: Boolean = false,
    val members: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTime: String = "",
    val unread: Int = 0
)

data class ChatRoomsResponse(val rooms: List<ChatRoom>)
data class ChatMessagesResponse(val messages: List<ChatMessage>)
data class SendMessageRequest(val roomId: String, val text: String, val replyTo: String? = null)

// New chat models
data class ChatUser(val _id: String, val channelName: String, val profilePic: String = "")
data class ChatUsersResponse(val users: List<ChatUser>)
data class CreateDmRequest(val targetUserId: String)
data class CreateDmResponse(val room: ChatRoom)
data class CreateGroupRequest(val name: String, val memberIds: List<String>)
data class CreateGroupResponse(val room: ChatRoom)
data class GroupInviteRequest(val roomId: String, val targetUserId: String)
data class GroupAcceptRequest(val roomId: String)
data class ReactRequest(val msgId: String, val emoji: String)
data class ReactResponse(val success: Boolean, val reactions: Map<String, Int>)
data class StarRequest(val msgId: String)
data class StarResponse(val success: Boolean, val starred: Boolean)
data class PinRequest(val msgId: String, val roomId: String)
data class DeleteMsgResponse(val success: Boolean)


