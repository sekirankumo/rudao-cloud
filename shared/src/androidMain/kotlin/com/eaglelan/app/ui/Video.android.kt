package com.eaglelan.app.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class ExoVideoPlayer(context: Context, http: OkHttpClient, kind: PlayerKind) : VideoPlayer {

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
        .apply {
            // 随机速览: small buffer (a clip only needs ~10s) and keyframe seeks so jumping in is quick.
            if (kind == PlayerKind.PREVIEW) {
                setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 15_000, 1_000, 2_000).build())
            }
        }
        .build()
        .apply {
            if (kind == PlayerKind.PREVIEW) setSeekParameters(SeekParameters.CLOSEST_SYNC)
            else repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    rendered = true
                }
            })
        }

    private var rendered = false

    override var volume: Float
        get() = exo.volume
        set(v) {
            exo.volume = v
        }
    override var playWhenReady: Boolean
        get() = exo.playWhenReady
        set(v) {
            exo.playWhenReady = v
        }
    override val isPlaying get() = exo.isPlaying
    override val isBuffering get() = exo.playbackState == Player.STATE_BUFFERING
    override val isEnded get() = exo.playbackState == Player.STATE_ENDED
    override val hasError get() = exo.playerError != null
    override val hasFrame get() = rendered
    override val currentPosition get() = exo.currentPosition
    override val duration get() = exo.duration.coerceAtLeast(0L)
    override val bufferedPosition get() = exo.bufferedPosition

    override fun setUrl(url: String) {
        rendered = false
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
    }

    override fun seekTo(positionMs: Long) = exo.seekTo(positionMs)
    override fun setSpeed(speed: Float) = exo.setPlaybackSpeed(speed)
    override fun release() = exo.release()
    override fun play() = exo.play()
    override fun pause() = exo.pause()
}

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(player: VideoPlayer, keepScreenOn: Boolean, modifier: Modifier, keepContentOnPlayerReset: Boolean) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(keepContentOnPlayerReset)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = {
            it.player = (player as ExoVideoPlayer).exo
            it.keepScreenOn = keepScreenOn
        },
        modifier = modifier,
    )
}

actual val videoSurfaceCoversPoster = false
