package com.mumeinosato.musicplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

data class Track(val title: String, val artist: String = "", val id: String = "")

private val sampleTracks = listOf(
    Track("Midnight Drive", "Neon Harbor"),
    Track("Glass Rain", "Aoi Sakura"),
    Track("Afterglow", "Lumen"),
    Track("Paper Planes", "The Quiet Hours"),
    Track("Blue Hour", "Mio"),
    Track("Static Bloom", "Kairo"),
    Track("Slow Orbit", "Nightfall Club"),
    Track("Echoes of You", "Haru"),
)

@Composable
fun Main_Layout(
    tracks: List<Track> = sampleTracks,
    currentIndex: Int = 0,
    isPlaying: Boolean = false,
    isSyncing: Boolean = false,
    status: String = "Synced",
    onPlayToggle: () -> Unit = {},
    onTrackSelected: (Int) -> Unit = {},
    onSync: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.surfaceContainer, colors.background)))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Header(status = status, isSyncing = isSyncing, onSync = { if (!isSyncing) onSync() })

        Spacer(Modifier.height(16.dp))

        NowPlayingCard(
            track = tracks.getOrNull(currentIndex),
            isPlaying = isPlaying,
            onToggle = onPlayToggle,
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PLAYLIST", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text("${tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(tracks) { index, track ->
                TrackRow(
                    index = index,
                    track = track,
                    selected = index == currentIndex,
                    playing = index == currentIndex && isPlaying,
                    onClick = { onTrackSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun Header(status: String, isSyncing: Boolean, onSync: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Player", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SyncButton(isSyncing = isSyncing, onClick = onSync)
    }
}

@Composable
private fun SyncButton(isSyncing: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(
        if (isSyncing) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.3f),
        label = "syncBorder",
    )
    val iconColor = if (isSyncing) colors.primary else colors.onSurface

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 同期中だけ回す。止まっている間は毎フレームの再描画をしない
        if (isSyncing) {
            SpinningSyncIcon(iconColor)
        } else {
            Canvas(Modifier.size(22.dp)) { drawSyncIcon(iconColor) }
        }
    }
}

@Composable
private fun SpinningSyncIcon(color: Color) {
    val transition = rememberInfiniteTransition(label = "sync")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "syncAngle",
    )
    // 角度は描画フェーズで読み、毎フレームの再コンポーズを避ける
    Canvas(
        Modifier
            .size(22.dp)
            .graphicsLayer { rotationZ = angle }
    ) { drawSyncIcon(color) }
}

@Composable
private fun NowPlayingCard(track: Track?, isPlaying: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val onCard = colors.onPrimaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.primaryContainer, colors.secondaryContainer, colors.tertiaryContainer)
                )
            )
            .padding(24.dp)
    ) {
        Text(
            if (isPlaying) "NOW PLAYING" else "PAUSED",
            style = MaterialTheme.typography.labelMedium,
            color = onCard.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: "No track",
                    style = MaterialTheme.typography.titleLarge,
                    color = onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(onCard)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(26.dp)) {
                    if (isPlaying) drawPauseIcon(colors.tertiaryContainer) else drawPlayIcon(colors.tertiaryContainer)
                }
            }
        }
    }
}

@Composable
private fun TrackRow(index: Int, track: Track, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "rowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                Canvas(Modifier.size(14.dp)) { drawPlayIcon(colors.primary) }
            } else {
                Text(
                    "${index + 1}".padStart(2, '0'),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun DrawScope.drawPlayIcon(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.15f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width * 0.15f, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPauseIcon(color: Color) {
    val barW = size.width * 0.3f
    val r = CornerRadius(barW * 0.25f)
    drawRoundRect(color, Offset(size.width * 0.08f, 0f), Size(barW, size.height), r)
    drawRoundRect(color, Offset(size.width - size.width * 0.08f - barW, 0f), Size(barW, size.height), r)
}

private fun DrawScope.drawSyncIcon(color: Color) {
    val stroke = size.minDimension * 0.1f
    val radius = size.minDimension / 2f - stroke * 1.5f
    val center = Offset(size.width / 2f, size.height / 2f)
    val startDeg = -60f
    val sweepDeg = 290f
    drawArc(
        color = color,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    val endRad = Math.toRadians((startDeg + sweepDeg).toDouble())
    val end = Offset(center.x + radius * cos(endRad).toFloat(), center.y + radius * sin(endRad).toFloat())
    val tangent = Offset(-sin(endRad).toFloat(), cos(endRad).toFloat())
    val normal = Offset(cos(endRad).toFloat(), sin(endRad).toFloat())
    val k = stroke * 2.2f
    val head = Path().apply {
        moveTo(end.x + tangent.x * k, end.y + tangent.y * k)
        lineTo(end.x + normal.x * k, end.y + normal.y * k)
        lineTo(end.x - normal.x * k, end.y - normal.y * k)
        close()
    }
    drawPath(head, color)
}
