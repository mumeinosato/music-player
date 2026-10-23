package com.mumeinosato.musicplayer.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** バックグラウンド再生用。ここで再生するのは file:// のローカルファイルのみ */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

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
                // 常にシャッフル＋全曲ループ
                shuffleModeEnabled = true
                repeatMode = Player.REPEAT_MODE_ALL
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

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
