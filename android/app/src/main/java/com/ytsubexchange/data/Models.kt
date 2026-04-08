package com.ytsubexchange.data

data class UserProfile(
    val _id: String = "",
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
    val coinsReward: Int = 1,
    val cardType: String = "" // "owner", "p2p", "online"
)

data class BuySubsRequest(val coins: Int)
data class BuySubsResponse(val success: Boolean, val remainingCoins: Int, val message: String? = null)
data class CoinsEarned(val coins: Int, val earned: Int)
data class CoinRequestBody(val coins: Int)
data class CoinRequestResponse(val success: Boolean, val requestId: String)
data class NoticeData(val _id: String, val title: String, val message: String, val subAdminCode: String? = null)
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
    val referral: ReferralResponse? = null
)

data class VersionResponse(
    val latestVersion: Int,
    val versionName: String,
    val forceUpdate: Boolean = false,
    val downloadUrl: String,
    val changelog: String = ""
)

// ── Chat Models ───────────────────────────────────────────────
data class ReplyRef(val msgId: String, val text: String, val senderName: String)
data class ChatMessage(
    val _id: String,
    val senderId: String,
    val senderName: String,
    val senderPic: String = "",
    val text: String,
    val createdAt: String,
    val read: Boolean = false,
    val readBy: List<String> = emptyList(),
    val pinned: Boolean = false,
    val starred: Boolean = false,
    val edited: Boolean = false,
    val reactions: Map<String, List<String>> = emptyMap(),
    val replyTo: ReplyRef? = null,
    val forwardedFrom: String? = null,
    val disappearsAt: String? = null,
    val fileUrl: String? = null,
    val fileType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)
data class ChatRoom(
    val _id: String,
    val name: String,
    val pic: String? = null,
    val description: String? = null,
    val isGroup: Boolean = false,
    val members: List<String>? = null,
    val lastMessage: String? = null,
    val lastTime: String? = null,
    val unread: Int = 0,
    val otherUserId: String? = null,
    val isBlockedByMe: Boolean = false,
    val isBlockedByThem: Boolean = false,
    val slowMode: Int = 0,
    val disappearingSeconds: Int = 0,
    val inviteToken: String? = null,
    val subAdmins: List<GroupSubAdmin>? = null
)

data class GroupSubAdmin(
    val userId: String,
    val canDeleteMessages: Boolean = true,
    val canBanMembers: Boolean = false,
    val canInviteMembers: Boolean = true,
    val canPinMessages: Boolean = false,
    val canChangeGroupInfo: Boolean = false,
    val canStartVoiceChat: Boolean = false
)

data class GroupSearchResult(val _id: String, val name: String, val pic: String = "", val description: String? = null, val memberCount: Int = 0, val hasInviteLink: Boolean = false, val isMember: Boolean = false)
data class GroupSearchResponse(val groups: List<GroupSearchResult>)
data class GroupInviteLinkResponse(val success: Boolean, val inviteLink: String = "", val token: String = "")
data class SubAdminPermissions(
    val canDeleteMessages: Boolean = true,
    val canBanMembers: Boolean = false,
    val canInviteMembers: Boolean = true,
    val canPinMessages: Boolean = false,
    val canChangeGroupInfo: Boolean = false,
    val canStartVoiceChat: Boolean = false
)
data class ManageSubAdminRequest(
    val roomId: String,
    val targetUserId: String,
    val action: String,
    val permissions: SubAdminPermissions? = null
)
data class GroupJoinByLinkResponse(val success: Boolean, val alreadyMember: Boolean = false, val room: ChatRoom? = null)

// ── AI Companion ──────────────────────────────────────────────
data class AiChatResponse(val reply: String, val success: Boolean = true)
data class AiMessage(val role: String, val text: String)

// ── Promo Videos ──────────────────────────────────────────────
data class PromoVideo(
    val _id: String,
    val title: String = "",
    val youtubeUrl: String,
    val channelName: String = "",
    val type: String = "short",
    val coinsReward: Int = 5,
    val watchSeconds: Int = 60,
    val isActive: Boolean = true
)
data class PromoVideosResponse(val videos: List<PromoVideo>) // role: "user" or "ai"

data class BlockedUser(
    val userId: String,
    val name: String,
    val pic: String = "",
    val roomId: String
)

data class BlockedUsersResponse(val blockedUsers: List<BlockedUser>)
data class ChatRoomsResponse(val rooms: List<ChatRoom>)
data class ChatMessagesResponse(val messages: List<ChatMessage>)
data class SendMessageRequest(val roomId: String, val text: String, val replyTo: String? = null)
data class ChatUser(val _id: String, val channelName: String, val profilePic: String)
data class ChatUsersResponse(val users: List<ChatUser>)
data class CreateDmRequest(val targetUserId: String)
data class CreateDmResponse(val room: ChatRoom)
data class CreateGroupRequest(val name: String, val memberIds: List<String>)
data class CreateGroupResponse(val room: ChatRoom)
data class GroupInviteRequest(val roomId: String, val targetUserId: String)
data class GroupAcceptRequest(val roomId: String)
data class GroupRejectRequest(val roomId: String)
data class GroupRemoveRequest(val roomId: String, val targetUserId: String)
data class GroupLeaveRequest(val roomId: String)
data class GroupMember(val _id: String, val channelName: String, val profilePic: String)
data class GroupInfoResponse(
    val room: ChatRoom,
    val members: List<GroupMember>,
    val isAdmin: Boolean
)
data class ReactRequest(val msgId: String, val emoji: String)
data class ReactResponse(val success: Boolean, val reactions: Map<String, List<String>>)
data class StarRequest(val msgId: String)
data class StarResponse(val success: Boolean, val starred: Boolean)
data class PinRequest(val msgId: String, val roomId: String)
data class DeleteMsgResponse(val success: Boolean)
data class EditMsgResponse(val success: Boolean, val text: String = "", val edited: Boolean = true)
data class MuteResponse(val success: Boolean, val muted: Boolean)

// ── Chat Request Models ───────────────────────────────────────
data class ChatRequest(
    val requestId: String,
    val fromUserId: String,
    val fromName: String,
    val fromPic: String = "",
    val createdAt: String = ""
)
data class PendingRequestsResponse(val requests: List<ChatRequest>)
data class SendChatRequestRequest(val targetUserId: String)
data class SendChatRequestResponse(val success: Boolean = false, val requestId: String = "", val alreadyConnected: Boolean = false, val room: ChatRoom? = null)
data class AcceptChatRequestRequest(val requestId: String)
data class RejectChatRequestRequest(val requestId: String)

data class SentRequest(
    val requestId: String,
    val toUserId: String,
    val toName: String,
    val toPic: String = "",
    val createdAt: String = ""
)
data class SentRequestsResponse(val requests: List<SentRequest>)

data class SetDisappearingRequest(val roomId: String, val seconds: Int)
data class GroupInviteNotif(val roomId: String, val roomName: String, val invitedBy: String, val invitedByPic: String)

data class OnlineStatusResponse(val onlineUsers: List<String>)
data class BlockResponse(val success: Boolean)

// ── Offline Message & Call History Models ────────────────────
data class OfflineMessage(
    val _id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val senderPic: String,
    val text: String,
    val createdAt: String,
    val fileUrl: String? = null,
    val fileType: String? = null,
    val fileName: String? = null
)

data class OfflineMessagesResponse(
    val messages: List<OfflineMessage>,
    val count: Int
)

data class CallLogEntry(
    val _id: String,
    val roomId: String,
    val callerId: String,
    val callerName: String,
    val callType: String, // "voice" or "video"
    val status: String, // "ringing", "connected", "ended", "missed", "declined"
    val startTime: String,
    val connectTime: String? = null,
    val endTime: String? = null,
    val duration: Int = 0, // seconds
    val isMissed: Boolean = false,
    val isDeclined: Boolean = false
)

data class CallHistoryResponse(
    val calls: List<CallLogEntry>,
    val count: Int
)

// ── Group Voice Chat Models ───────────────────────────────────
data class VoiceChatParticipant(
    val userId: String,
    val socketId: String,
    val name: String,
    val pic: String = "",
    val muted: Boolean = false,
    val isSpeaking: Boolean = false
)



// ── Review Models ─────────────────────────────────────────────
data class ReviewRequest(val rating: Int, val comment: String, val appVersion: String = "1.0.0")
data class ReviewResponse(val success: Boolean, val review: AppReview? = null)
data class MyReviewResponse(val review: AppReview? = null)
data class AppReview(
    val _id: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val appVersion: String = "",
    val createdAt: String = ""
)
