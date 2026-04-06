package com.ytsubexchange.test.network

import com.ytsubexchange.test.data.*
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
    suspend fun getChatRooms(@Header("Authorization") token: String): com.ytsubexchange.test.data.ChatRoomsResponse

    @GET("chat/messages/{roomId}")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): com.ytsubexchange.test.data.ChatMessagesResponse

    @POST("chat/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.SendMessageRequest
    ): com.ytsubexchange.test.data.ChatMessage

    @POST("chat/dm")
    suspend fun createDm(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.CreateDmRequest
    ): com.ytsubexchange.test.data.CreateDmResponse

    @POST("chat/group/create")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.CreateGroupRequest
    ): com.ytsubexchange.test.data.CreateGroupResponse

    @POST("chat/group/invite")
    suspend fun groupInvite(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.GroupInviteRequest
    ): retrofit2.Response<Void>

    @POST("chat/group/accept")
    suspend fun groupAccept(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.GroupAcceptRequest
    ): retrofit2.Response<Void>

    @GET("chat/users")
    suspend fun getChatUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String = ""
    ): com.ytsubexchange.test.data.ChatUsersResponse

    @POST("chat/react")
    suspend fun reactToMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.ReactRequest
    ): com.ytsubexchange.test.data.ReactResponse

    @POST("chat/star")
    suspend fun starMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.StarRequest
    ): com.ytsubexchange.test.data.StarResponse

    @GET("chat/starred/{roomId}")
    suspend fun getStarredMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): com.ytsubexchange.test.data.ChatMessagesResponse

    @POST("chat/pin")
    suspend fun pinMessage(
        @Header("Authorization") token: String,
        @Body request: com.ytsubexchange.test.data.PinRequest
    ): com.ytsubexchange.test.data.DeleteMsgResponse

    @DELETE("chat/message/{msgId}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("msgId") msgId: String
    ): com.ytsubexchange.test.data.DeleteMsgResponse

    // ── Community Chat ────────────────────────────────────────
    @GET("chat/community")
    suspend fun getCommunityMessages(@Header("Authorization") token: String): com.ytsubexchange.test.data.ChatMessagesResponse

    @POST("chat/community/send")
    suspend fun sendCommunityMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.test.data.ChatMessage

    // ── Video Exchange ────────────────────────────────────────
    @POST("user/watch-reward")
    suspend fun claimWatchReward(
        @Header("Authorization") token: String,
        @Body body: Map<String, Int>
    ): com.ytsubexchange.test.data.DailyBonusResponse

    @POST("user/video-order")
    suspend fun submitVideoOrder(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): com.ytsubexchange.test.data.BuySubsResponse
}
