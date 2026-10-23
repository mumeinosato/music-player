package com.mumeinosato.musicplayer.player

import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** バックグラウンド再生用。ここで再生するのは file:// のローカルファイルのみ */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    /** 「急に止まる」原因の切り分け用。止まった直後の logcat（タグ Playback）で理由が分かる */
    private val stopLogger = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.i(TAG, "playWhenReady=$playWhenReady reason=$reason")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "state=$playbackState")
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            Log.i(TAG, "suppression=$playbackSuppressionReason")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "error=${error.errorCodeName}", error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            // 画面消灯中も CPU を維持して、ローカルファイルの再生を途切れさせない
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                // 音声オフロードは使わない。画面消灯中に CPU が眠ったまま曲の切り替えを迎えると、
                // 次の曲を DSP に渡せず再生が止まる端末があるため（ローカル再生ならデコードも軽い）
                // 常にシャッフル＋全曲ループ
                shuffleModeEnabled = true
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(stopLogger)
            }
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** 最近のアプリ画面からスワイプで消されたら、再生中でも止めてサービスを終わらせる */
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.run {
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

    private companion object {
        const val TAG = "Playback"
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
