package com.mumeinosato.musicplayer

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import com.mumeinosato.musicplayer.ui.Main_Layout
import com.mumeinosato.musicplayer.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 再生通知を出すために通知権限を要求する
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            MusicPlayerTheme {
                Main_Layout(
                    tracks = viewModel.tracks,
                    currentIndex = viewModel.currentIndex,
                    isPlaying = viewModel.isPlaying,
                    isSyncing = viewModel.isSyncing,
                    status = viewModel.status,
                    onPlayToggle = viewModel::togglePlay,
                    onTrackSelected = viewModel::playTrack,
                    onSync = viewModel::sync,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainLayoutPreview(){
    MusicPlayerTheme {
        Main_Layout()
    }
}
