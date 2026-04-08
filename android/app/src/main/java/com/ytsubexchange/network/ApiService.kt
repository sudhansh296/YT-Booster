package com.ytsubexchange.network

import com.ytsubexchange.data.*
import retrofit2.http.*

interface ApiService {
    @GET("user/me")
    suspend fun getProfile(@Header("Authorization") token: String): UserProfile

    @POST("user/daily-bonus")
    suspend fun claimDailyBonus(@Header("Authorization") token: String): DailyBonusResponse

    @POST("user/buy-subscribers")
    suspend fun buySubscribers(
        @Header("Authorization") token: String,
        @Body request: BuySubsRequest
    ): BuySubsResponse

    @POST("user/coin-request")
    suspend fun submitCoinRequest(
        @Header("Authorization") token: String,
        @Body request: CoinRequestBody
    ): CoinRequestResponse

    @GET("user/notices")
    suspend fun getNotices(@Header("Authorization") token: String): List<NoticeData>

    @GET("user/transactions")
    suspend fun getTransactions(@Header("Authorization") token: String): List<TransactionData>

    @GET("user/leaderboard")
    suspend fun getLeaderboard(@Header("Authorization") token: String): LeaderboardResponse

    @GET("user/referral")
    suspend fun getReferral(@Header("Authorization") token: String): ReferralResponse

    @POST("user/referral/apply")
    suspend fun applyReferral(
        @Header("Authorization") token: String,
        @Body request: ReferralApplyRequest
    ): ReferralApplyResponse

    @GET("user/streak")
    suspend fun getStreak(@Header("Authorization") token: String): StreakResponse

    @GET("user/init")
    suspend fun getInit(@Header("Authorization") token: String): InitResponse

    @GET("version")
    fun checkVersion(): retrofit2.Call<VersionResponse>

    @POST("user/fcm-token")
    fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): retrofit2.Call<Void>

    // ── Chat ──────────────────────────────────────────────────
    @GET("chat/rooms")
    suspend fun getChatRooms(@Header("Authorization") token: String): com.ytsubexchange.data.ChatRoomsResponse

    @GET("chat/messages/{roomId}")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): com.ytsubexchange.data.ChatMessagesResponse

    @POST("chat/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.SendMessageRequest
    ): com.ytsubexchange.data.ChatMessage

    @POST("chat/dm")
    suspend fun createDm(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.CreateDmRequest
    ): com.ytsubexchange.data.CreateDmResponse

    @POST("chat/group/create")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.CreateGroupRequest
    ): com.ytsubexchange.data.CreateGroupResponse

    @POST("chat/group/invite")
    suspend fun groupInvite(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.GroupInviteRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/group/accept")
    suspend fun groupAccept(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.GroupAcceptRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/group/reject")
    suspend fun groupReject(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.GroupRejectRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @GET("chat/group/info/{roomId}")
    suspend fun getGroupInfo(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): com.ytsubexchange.data.GroupInfoResponse

    @POST("chat/group/remove")
    suspend fun groupRemoveMember(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.GroupRemoveRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/group/leave")
    suspend fun groupLeave(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.GroupLeaveRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @GET("chat/users")
    suspend fun getChatUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String = ""
    ): com.ytsubexchange.data.ChatUsersResponse

    @POST("chat/react")
    suspend fun reactToMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.ReactRequest
    ): com.ytsubexchange.data.ReactResponse

    @POST("chat/star")
    suspend fun starMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.StarRequest
    ): com.ytsubexchange.data.StarResponse

    @GET("chat/starred/{roomId}")
    suspend fun getStarredMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): com.ytsubexchange.data.ChatMessagesResponse

    @POST("chat/pin")
    suspend fun pinMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.PinRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @DELETE("chat/message/{msgId}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("msgId") msgId: String
    ): com.ytsubexchange.data.DeleteMsgResponse

    @PUT("chat/message/{msgId}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("msgId") msgId: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.EditMsgResponse

    @GET("chat/community")
    suspend fun getCommunityMessages(@Header("Authorization") token: String): com.ytsubexchange.data.ChatMessagesResponse

    @POST("chat/community/send")
    suspend fun sendCommunityMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.ChatMessage

    // ── Chat Requests ─────────────────────────────────────────
    @POST("chat/request/send")
    suspend fun sendChatRequest(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.SendChatRequestRequest
    ): com.ytsubexchange.data.SendChatRequestResponse

    @POST("chat/request/accept")
    suspend fun acceptChatRequest(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.AcceptChatRequestRequest
    ): com.ytsubexchange.data.SendChatRequestResponse

    @POST("chat/request/reject")
    suspend fun rejectChatRequest(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.RejectChatRequestRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @GET("chat/requests/pending")
    suspend fun getPendingRequests(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.PendingRequestsResponse

    @GET("chat/requests/sent")
    suspend fun getSentRequests(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.SentRequestsResponse

    // ── Video Exchange ────────────────────────────────────────
    @POST("user/watch-reward")
    suspend fun claimWatchReward(
        @Header("Authorization") token: String,
        @Body body: Map<String, Int>
    ): com.ytsubexchange.data.DailyBonusResponse

    @POST("user/video-order")
    suspend fun submitVideoOrder(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.BuySubsResponse

    // ── Chat Room Actions ─────────────────────────────────────
    @POST("chat/clear")
    suspend fun clearChat(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/mute")
    suspend fun muteChat(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.MuteResponse

    @POST("chat/block")
    suspend fun blockUser(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/unblock")
    suspend fun unblockUser(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.DeleteMsgResponse

    @GET("chat/blocked-users")
    suspend fun getBlockedUsers(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.BlockedUsersResponse

    @POST("chat/online-status")
    suspend fun getOnlineStatus(
        @Header("Authorization") token: String,
        @Body body: Map<String, List<String>>
    ): com.ytsubexchange.data.OnlineStatusResponse

    @Multipart
    @POST("chat/upload")
    suspend fun uploadChatFile(
        @Header("Authorization") token: String,
        @Part file: okhttp3.MultipartBody.Part,
        @Part("roomId") roomId: okhttp3.RequestBody
    ): com.ytsubexchange.data.ChatMessage

    // ── Group Advanced Features ───────────────────────────────
    @Multipart
    @POST("chat/group/settings")
    suspend fun updateGroupSettings(
        @Header("Authorization") token: String,
        @Part("roomId") roomId: okhttp3.RequestBody,
        @Part("name") name: okhttp3.RequestBody? = null,
        @Part("description") description: okhttp3.RequestBody? = null,
        @Part pic: okhttp3.MultipartBody.Part? = null
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/group/slow-mode")
    suspend fun setSlowMode(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/group/subadmin")
    suspend fun manageSubAdmin(
        @Header("Authorization") token: String,
        @Body body: com.ytsubexchange.data.ManageSubAdminRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/group/invite-link")
    suspend fun getGroupInviteLink(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.GroupInviteLinkResponse

    @POST("chat/group/join")
    suspend fun joinGroupDirect(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.GroupJoinByLinkResponse

    @POST("chat/group/join-by-link")
    suspend fun joinGroupByLink(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.GroupJoinByLinkResponse

    @GET("chat/groups/search")
    suspend fun searchGroups(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): com.ytsubexchange.data.GroupSearchResponse

    // ── Forward / Disappearing / Read ─────────────────────────
    @POST("chat/forward")
    suspend fun forwardMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/disappearing")
    suspend fun setDisappearing(
        @Header("Authorization") token: String,
        @Body body: com.ytsubexchange.data.SetDisappearingRequest
    ): com.ytsubexchange.data.DeleteMsgResponse

    @POST("chat/read")
    suspend fun markRead(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): com.ytsubexchange.data.DeleteMsgResponse

    // ── AI Companion ──────────────────────────────────────────
    @POST("ai/chat")
    suspend fun aiChat(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): com.ytsubexchange.data.AiChatResponse

    // ── Promo Videos ──────────────────────────────────────────
    @GET("user/promo-videos")
    suspend fun getPromoVideos(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.PromoVideosResponse

    @POST("user/promo-video-reward")
    suspend fun claimPromoReward(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.data.DailyBonusResponse

    // ── Offline Message & Call History APIs ──────────────────
    @GET("chat/offline-messages")
    suspend fun getOfflineMessages(
        @Header("Authorization") token: String,
        @Query("since") since: String? = null
    ): com.ytsubexchange.data.OfflineMessagesResponse

    @GET("chat/call-history")
    suspend fun getCallHistory(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50
    ): com.ytsubexchange.data.CallHistoryResponse

    @POST("chat/mark-read")
    suspend fun markMessagesRead(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): com.ytsubexchange.data.DeleteMsgResponse

    // ── Review ────────────────────────────────────────────────
    @POST("review")
    suspend fun submitReview(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.data.ReviewRequest
    ): com.ytsubexchange.data.ReviewResponse

    @GET("review/mine")
    suspend fun getMyReview(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.MyReviewResponse

    // ── Daily Tasks ───────────────────────────────────────────
    @GET("tasks/today")
    suspend fun getDailyTasks(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.DailyTasksResponse

    @POST("tasks/{taskId}/claim")
    suspend fun claimTask(
        @Header("Authorization") token: String,
        @Path("taskId") taskId: String
    ): com.ytsubexchange.data.ClaimTaskResponse

    @POST("tasks/login-task")
    suspend fun completeLoginTask(
        @Header("Authorization") token: String
    ): com.ytsubexchange.data.ClaimTaskResponse

    @POST("tasks/progress")
    suspend fun updateTaskProgress(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): com.ytsubexchange.data.ClaimTaskResponse
}
