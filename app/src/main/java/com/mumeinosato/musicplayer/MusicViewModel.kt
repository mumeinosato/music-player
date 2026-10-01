package com.mumeinosato.musicplayer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mumeinosato.musicplayer.data.MusicRepository
import com.mumeinosato.musicplayer.data.SyncStart
import com.mumeinosato.musicplayer.player.PlaybackService
import com.mumeinosato.musicplayer.ui.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private var controller: MediaController? = null

    /** コントローラー接続前に押された操作。接続したら実行する */
    private var pendingAction: (() -> Unit)? = null

    /** 連続した再生エラー数。全曲エラーのときに無限スキップして電池を食わないための歯止め */
    private var consecutiveErrors = 0

    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var currentIndex by mutableIntStateOf(0); private set
    var isPlaying by mutableStateOf(false); private set
    var isSyncing by mutableStateOf(false); private set
    var status by mutableStateOf("Synced"); private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@MusicViewModel.isPlaying = isPlaying
            if (isPlaying) consecutiveErrors = 0
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.currentMediaItemIndex?.let { currentIndex = it }
        }

        override fun onPlayerError(error: PlaybackException) {
            // ファイルが壊れている等で再生できない曲は飛ばす
            val c = controller ?: return
            if (++consecutiveErrors >= c.mediaItemCount) {
                c.stop()
                return
            }
            c.seekToNextMediaItem()
            c.prepare()
        }
    }

    init {
        viewModelScope.launch {
            tracks = withContext(Dispatchers.IO) { repo.localTracks() }
        }
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            // アプリを開き直したとき、既に再生中ならその状態を反映する
            if (c.mediaItemCount > 0) {
                currentIndex = c.currentMediaItemIndex
                isPlaying = c.isPlaying
            }
            pendingAction?.invoke()
            pendingAction = null
        }, ContextCompat.getMainExecutor(app))
    }

    fun sync() {
        if (isSyncing) return
        isSyncing = true
        status = "Syncing with server…"
        viewModelScope.launch {
            status = try {
                withContext(Dispatchers.IO) { runSync() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            }
            reloadTracks()
            isSyncing = false
        }
    }

    /** サーバーに同期を依頼 → 終わるまで10秒ごとに /is_syncing を見る → /latest を取って反映 */
    private suspend fun runSync(): String {
        when (val start = repo.api.startSync()) {
            is SyncStart.AuthRequired -> {
                if (start.url.isEmpty()) return "Auth required"
                openAuthPage(start.url)
                return "Auth required (opened browser)"
            }
            SyncStart.UpToDate -> {}
            SyncStart.Running -> {
                status = "Server is syncing…"
                // サーバーが終わらないときに永遠にポーリングして電池を使わないよう上限を付ける
                val finished = withTimeoutOrNull(POLL_TIMEOUT_MS) {
                    do {
                        delay(POLL_INTERVAL_MS)
                    } while (repo.api.isSyncing())
                    true
                }
                if (finished == null) return "Sync timed out"
            }
        }
        val failed = repo.fetchLatest { status = it }
        return if (failed == 0) "Synced" else "Synced ($failed failed)"
    }

    private fun openAuthPage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    private suspend fun reloadTracks() {
        val playingId = tracks.getOrNull(currentIndex)?.id
        val newTracks = withContext(Dispatchers.IO) { repo.localTracks() }
        val queueChanged = newTracks.map { it.id } != tracks.map { it.id }
        tracks = newTracks
        // 再生中の曲がリスト変更後も選択されたままになるようにする
        newTracks.indexOfFirst { it.id == playingId }.takeIf { it >= 0 }?.let { currentIndex = it }
        if (currentIndex >= newTracks.size) currentIndex = 0
        if (queueChanged) refreshQueue(playingId)
    }

    /** 同期で削除された曲が再生キューに残らないよう、キューを最新の曲リストに合わせる */
    private fun refreshQueue(playingId: String?) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val index = tracks.indexOfFirst { it.id == playingId }
        if (index >= 0) {
            // 再生中の曲はそのまま、位置も保ってキューだけ入れ替える
            c.setMediaItems(tracks.map(::mediaItemOf), index, c.currentPosition)
        } else {
            c.stop()
            c.clearMediaItems()
        }
    }

    fun playTrack(index: Int) = whenConnected { c ->
        if (index !in tracks.indices) return@whenConnected
        consecutiveErrors = 0
        // 再生キューは internal storage 上のファイルだけで組む
        c.setMediaItems(tracks.map(::mediaItemOf), index, 0L)
        c.prepare()
        c.play()
        currentIndex = index
    }

    fun togglePlay() = whenConnected { c ->
        when {
            // プレイリストの開始曲はランダム
            c.mediaItemCount == 0 -> if (tracks.isNotEmpty()) playTrack(tracks.indices.random())
            c.isPlaying -> c.pause()
            else -> c.play()
        }
    }

    private fun whenConnected(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pendingAction = { controller?.let(action) }
    }

    private fun mediaItemOf(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(repo.fileOf(track.id).toUri())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()

    private companion object {
        const val POLL_INTERVAL_MS = 10_000L
        const val POLL_TIMEOUT_MS = 30 * 60 * 1000L
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
