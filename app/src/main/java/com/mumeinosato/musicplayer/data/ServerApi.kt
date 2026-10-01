package com.mumeinosato.musicplayer.data

import com.mumeinosato.musicplayer.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** music-server の /latest の結果 */
data class LatestDiff(
    val hash: String,
    /** true なら手元は最新。以降の項目は空なので何も触らない */
    val upToDate: Boolean,
    /** 手元との差分（add はダウンロード、remove はローカルから削除） */
    val add: List<String>,
    val remove: List<String>,
    /** 最新の全ID（再生順）と、それに対応する曲名 */
    val allIds: List<String>,
    val allNames: List<String>,
)

/** /sync を呼んだ結果 */
sealed interface SyncStart {
    /** サーバーがバックグラウンドで同期を開始した、または既に同期中 */
    data object Running : SyncStart

    /** 変更なし（すぐ /latest を取ってよい） */
    data object UpToDate : SyncStart

    /** YouTube の認可が必要 */
    data class AuthRequired(val url: String) : SyncStart
}

class ServerApi(private val baseUrl: String = BuildConfig.SERVER_URL.trimEnd('/')) {

    fun startSync(): SyncStart {
        val (code, json) = get("/sync")
        return when (code) {
            200 -> SyncStart.UpToDate
            202, 409 -> SyncStart.Running
            401 -> SyncStart.AuthRequired(json.optString("auth_url"))
            else -> error("GET /sync failed: HTTP $code ${json.optString("message")}")
        }
    }

    fun isSyncing(): Boolean {
        val (code, json) = get("/is_syncing")
        if (code != 200) error("GET /is_syncing failed: HTTP $code")
        return json.getBoolean("syncing")
    }

    fun getLatest(hash: String): LatestDiff {
        val (code, json) = get("/latest?hash=${URLEncoder.encode(hash, "UTF-8")}")
        if (code != 200) error("GET /latest failed: HTTP $code")
        return LatestDiff(
            hash = json.getString("hash"),
            upToDate = json.optBoolean("up_to_date"),
            add = json.strings("add"),
            remove = json.strings("remove"),
            allIds = json.strings("all_id"),
            allNames = json.strings("all_name"),
        )
    }

    private fun get(path: String): Pair<Int, JSONObject> {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                JSONObject()
            }
            return code to json
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.strings(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { arr.getString(it) }
    }
}
