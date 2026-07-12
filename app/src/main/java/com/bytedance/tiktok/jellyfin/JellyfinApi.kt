package com.bytedance.tiktok.jellyfin

import retrofit2.Response
import retrofit2.http.*

interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun login(
        @Header("X-Emby-Authorization") auth: String,
        @Body body: AuthRequest
    ): AuthResponse

    @GET("Users/{userId}/Views")
    suspend fun getViews(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String
    ): ItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeTypes: String? = null,
        @Query("Recursive") recursive: Boolean = false,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 100,
        @Query("Filters") filters: String? = null,
        @Query("SearchTerm") searchTerm: String? = null
    ): ItemsResponse

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") token: String
    ): JellyfinUserData

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") token: String
    ): JellyfinUserData

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Header("X-Emby-Token") token: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Header("X-Emby-Token") token: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Header("X-Emby-Token") token: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @GET("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") token: String,
        @Query("UserId") userId: String
    ): PlaybackInfoResponse
}
