package com.bytedance.tiktok.jellyfin

import com.google.gson.annotations.SerializedName

// Auth
data class AuthRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Pw") val password: String
)

data class AuthResponse(
    @SerializedName("AccessToken") val accessToken: String,
    @SerializedName("User") val user: JellyfinUser
)

data class JellyfinUser(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String
)

// Items
data class ItemsResponse(
    @SerializedName("Items") val items: List<JellyfinItem>,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int
)

data class JellyfinItem(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String,
    @SerializedName("Type") val type: String,
    @SerializedName("IsFolder") val isFolder: Boolean = false,
    @SerializedName("CollectionType") val collectionType: String? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("UserData") val userData: JellyfinUserData? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null
)

data class JellyfinUserData(
    @SerializedName("IsFavorite") val isFavorite: Boolean = false,
    @SerializedName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerializedName("PlayCount") val playCount: Int = 0
)

// PlaybackInfo
data class PlaybackInfoResponse(
    @SerializedName("MediaSources") val mediaSources: List<MediaSource>? = null
)

data class MediaSource(
    @SerializedName("MediaStreams") val mediaStreams: List<MediaStream>? = null
)

data class MediaStream(
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null
)
