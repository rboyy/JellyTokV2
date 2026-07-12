package com.bytedance.tiktok.jellyfin

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Represents a saved Jellyfin server configuration
 */
data class ServerConfig(
    val id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var userId: String = "",
    var accessToken: String = ""
)

/**
 * Singleton managing Jellyfin authentication, multi-server support, and API access
 */
object JellyfinManager {
    var serverUrl: String = ""
        private set
    var accessToken: String = ""
        private set
    var userId: String = ""
        private set

    private var api: JellyfinApi? = null
    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    private var servers: MutableList<ServerConfig> = mutableListOf()
    private var activeServerIndex: Int = 0

    fun init(context: Context) {
        prefs = context.getSharedPreferences("jellyfin", Context.MODE_PRIVATE)
        loadServers()
        val active = getActiveServer()
        if (active != null && active.accessToken.isNotEmpty()) {
            serverUrl = active.url
            accessToken = active.accessToken
            userId = active.userId
            rebuildApi()
        }
    }

    // ── Server Management ──

    fun getServers(): List<ServerConfig> = servers.toList()

    fun getActiveServerIndex(): Int = activeServerIndex

    fun getActiveServer(): ServerConfig? {
        return if (servers.isNotEmpty() && activeServerIndex in servers.indices) {
            servers[activeServerIndex]
        } else null
    }

    fun addServer(config: ServerConfig) {
        // Check if server URL already exists, update if so
        val existingIndex = servers.indexOfFirst { it.url.trimEnd('/') == config.url.trimEnd('/') }
        if (existingIndex >= 0) {
            servers[existingIndex] = config
        } else {
            servers.add(config)
        }
        saveServers()
    }

    fun removeServer(index: Int) {
        if (index < 0 || index >= servers.size) return
        servers.removeAt(index)
        if (activeServerIndex >= servers.size) {
            activeServerIndex = (servers.size - 1).coerceAtLeast(0)
        }
        saveServers()
    }

    suspend fun switchServer(index: Int): Boolean {
        if (index < 0 || index >= servers.size) return false
        val server = servers[index]
        return try {
            login(server.url, server.username, server.password, serverIndex = index)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun logout() {
        accessToken = ""
        userId = ""
        api = null
        val active = getActiveServer()
        if (active != null) {
            active.accessToken = ""
            active.userId = ""
            saveServers()
        }
    }

    // ── Backward-compatible methods ──

    fun isLoggedIn(): Boolean {
        return serverUrl.isNotEmpty() && accessToken.isNotEmpty()
    }

    fun loadSavedCredentials(): Triple<String, String, String>? {
        val active = getActiveServer() ?: return null
        if (active.accessToken.isEmpty()) return null
        return Triple(active.url, active.username, active.password)
    }

    // ── API Access ──

    private fun buildApi(baseUrl: String): JellyfinApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("${baseUrl.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JellyfinApi::class.java)
    }

    private fun rebuildApi() {
        if (serverUrl.isNotEmpty()) {
            api = buildApi(serverUrl)
        }
    }

    fun getApi(): JellyfinApi {
        if (api == null) rebuildApi()
        return api!!
    }

    private fun authHeader() =
        "MediaBrowser Client=\"JellyTok\",Device=\"Android\",DeviceId=\"jellytok-android\",Version=\"2.0\""

    suspend fun login(url: String, username: String, password: String, serverIndex: Int = -1): AuthResponse {
        val cleanUrl = url.trimEnd('/')
        val tempApi = buildApi(cleanUrl)
        val response = tempApi.login(authHeader(), AuthRequest(username, password))

        // Update active state
        serverUrl = cleanUrl
        accessToken = response.accessToken
        userId = response.user.id
        api = tempApi

        // Save or update server config
        val config = ServerConfig(
            name = cleanUrl.replace("https://", "").replace("http://", ""),
            url = cleanUrl,
            username = username,
            password = password,
            userId = response.user.id,
            accessToken = response.accessToken
        )

        if (serverIndex >= 0 && serverIndex < servers.size) {
            servers[serverIndex] = config
            activeServerIndex = serverIndex
        } else {
            val existingIndex = servers.indexOfFirst { it.url.trimEnd('/') == cleanUrl }
            if (existingIndex >= 0) {
                servers[existingIndex] = config
                activeServerIndex = existingIndex
            } else {
                servers.add(config)
                activeServerIndex = servers.size - 1
            }
        }
        saveServers()
        return response
    }

    // ── Persistence ──

    private fun loadServers() {
        val json = prefs?.getString("servers", null) ?: run {
            // Migration: load old single-server credentials
            val url = prefs?.getString("server_url", null)
            val user = prefs?.getString("username", null)
            val pass = prefs?.getString("password", null)
            if (url != null && user != null && pass != null) {
                val config = ServerConfig(
                    name = url.replace("https://", "").replace("http://", ""),
                    url = url,
                    username = user,
                    password = pass
                )
                servers = mutableListOf(config)
                activeServerIndex = 0
                saveServers()
            }
            return
        }
        try {
            val type = object : TypeToken<MutableList<ServerConfig>>() {}.type
            servers = gson.fromJson(json, type) ?: mutableListOf()
            activeServerIndex = prefs?.getInt("active_server_index", 0) ?: 0
        } catch (_: Exception) {
            servers = mutableListOf()
        }
    }

    private fun saveServers() {
        prefs?.edit()?.apply {
            putString("servers", gson.toJson(servers))
            putInt("active_server_index", activeServerIndex)
            // Also save legacy fields for backward compatibility
            val active = getActiveServer()
            if (active != null) {
                putString("server_url", active.url)
                putString("username", active.username)
                putString("password", active.password)
            }
            apply()
        }
    }

    // ── Local Playback Position Cache ──

    private val progressPrefs: SharedPreferences?
        get() = prefs  // reuse same "jellyfin" prefs

    /**
     * Save playback position locally. This works around Jellyfin server
     * resetting PositionTicks=0 when it marks a video as Played.
     */
    fun saveLocalPosition(itemId: String, positionMs: Long) {
        if (itemId.isEmpty() || positionMs <= 0) return
        progressPrefs?.edit()?.putLong("pos_$itemId", positionMs)?.apply()
    }

    /**
     * Get locally saved playback position in ms.
     * Returns 0 if no local position exists.
     */
    fun getLocalPosition(itemId: String): Long {
        if (itemId.isEmpty()) return 0L
        return progressPrefs?.getLong("pos_$itemId", 0L) ?: 0L
    }

    /**
     * Clear local position for an item (e.g. when video is fully watched).
     */
    fun clearLocalPosition(itemId: String) {
        if (itemId.isEmpty()) return
        progressPrefs?.edit()?.remove("pos_$itemId")?.apply()
    }

    // ── Content APIs ──

    fun getStreamUrl(itemId: String): String {
        return "${serverUrl}/Videos/$itemId/stream?static=true&api_key=$accessToken"
    }

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String {
        return "${serverUrl}/Items/$itemId/Images/Primary?maxWidth=$maxWidth&api_key=$accessToken"
    }

    suspend fun getVideosRecursive(parentId: String, limit: Int = 500): ItemsResponse {
        return getApi().getItems(
            userId, accessToken,
            parentId = parentId,
            includeTypes = "Video,Movie,Episode",
            recursive = true,
            sortBy = "SortName",
            limit = limit
        )
    }

    suspend fun getViews(): ItemsResponse {
        return getApi().getViews(userId, accessToken)
    }

    /**
     * Get direct children of a folder (non-recursive).
     * Returns both subfolders and videos for folder browsing.
     */
    suspend fun getChildren(parentId: String, limit: Int = 100): ItemsResponse {
        return getApi().getItems(
            userId, accessToken,
            parentId = parentId,
            includeTypes = "Folder,Video,Movie,Episode",
            recursive = false,
            sortBy = "SortName",
            limit = limit
        )
    }

    suspend fun getFavorites(limit: Int = 500): ItemsResponse {
        return getApi().getItems(
            userId, accessToken,
            includeTypes = "Video,Movie,Episode",
            recursive = true,
            sortBy = "SortName",
            limit = limit,
            filters = "IsFavorite"
        )
    }

    suspend fun searchVideos(term: String, limit: Int = 50): ItemsResponse {
        return getApi().getItems(
            userId, accessToken,
            includeTypes = "Video,Movie,Episode",
            recursive = true,
            sortBy = "SortName",
            limit = limit,
            searchTerm = term
        )
    }

    suspend fun toggleFavorite(itemId: String, isCurrentlyFavorite: Boolean): JellyfinUserData {
        return if (isCurrentlyFavorite) {
            getApi().removeFavorite(userId, itemId, accessToken)
        } else {
            getApi().addFavorite(userId, itemId, accessToken)
        }
    }

    suspend fun reportStart(itemId: String) {
        try {
            getApi().reportPlaybackStart(accessToken, mapOf(
                "ItemId" to itemId,
                "MediaSourceId" to itemId,
                "PlayMethod" to "DirectStream",
                "PlaybackStartTimeTicks" to (System.currentTimeMillis() * 10_000L)
            ))
        } catch (e: Exception) {
            android.util.Log.e("JELLYFIN_REPORT", "reportStart FAILED: ${e.message}", e)
        }
    }

    suspend fun reportProgress(itemId: String, positionMs: Long, isPaused: Boolean) {
        try {
            getApi().reportPlaybackProgress(accessToken, mapOf(
                "ItemId" to itemId,
                "MediaSourceId" to itemId,
                "PositionTicks" to (positionMs * 10_000L),
                "IsPaused" to isPaused,
                "PlayMethod" to "DirectStream",
                "EventName" to "timeupdate"
            ))
        } catch (e: Exception) {
            android.util.Log.e("JELLYFIN_REPORT", "reportProgress FAILED: ${e.message}", e)
        }
    }

    suspend fun reportStopped(itemId: String, positionMs: Long) {
        try {
            getApi().reportPlaybackStopped(accessToken, mapOf(
                "ItemId" to itemId,
                "MediaSourceId" to itemId,
                "PositionTicks" to (positionMs * 10_000L)
            ))
        } catch (e: Exception) {
            android.util.Log.e("JELLYFIN_REPORT", "reportStopped FAILED: ${e.message}", e)
        }
    }

    /**
     * Get video dimensions from PlaybackInfo API (MediaStreams).
     * Returns Pair(width, height) or null if unavailable.
     */
    suspend fun getVideoDimensions(itemId: String): Pair<Int, Int>? {
        return try {
            val info = getApi().getPlaybackInfo(itemId, accessToken, userId)
            val videoStream = info.mediaSources?.firstOrNull()?.mediaStreams
                ?.firstOrNull { it.type == "Video" }
            val w = videoStream?.width ?: 0
            val h = videoStream?.height ?: 0
            if (w > 0 && h > 0) Pair(w, h) else null
        } catch (_: Exception) {
            null
        }
    }
}
