package com.eaglelan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class PlayerKind {
    /** The normal player: loops the video, exact seeks. */
    FULL,
    /** 随机速览: small buffer and keyframe seeks so jumping into the middle is quick. */
    PREVIEW,
}

/**
 * The slice of a video player the screens use, modelled on ExoPlayer: Android wraps ExoPlayer,
 * iOS AVPlayer, Windows libVLC. Positions are in milliseconds; call everything from the main thread.
 */
interface VideoPlayer {
    var volume: Float
    /** Play as soon as enough is buffered (ExoPlayer's playWhenReady). */
    var playWhenReady: Boolean
    val isPlaying: Boolean
    val isBuffering: Boolean
    /** Reached the end ([PlayerKind.PREVIEW] only; FULL loops). */
    val isEnded: Boolean
    val hasError: Boolean
    /** Whether a video frame has been drawn since the last [setUrl]. */
    val hasFrame: Boolean
    val currentPosition: Long
    /** 0 while unknown. */
    val duration: Long
    val bufferedPosition: Long

    /** Loads a new video (paused unless [playWhenReady]). */
    fun setUrl(url: String)
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()

    fun play() {
        playWhenReady = true
    }

    fun pause() {
        playWhenReady = false
    }
}

/** Shows [player]'s picture, letterboxed to fit. Swapping [player] keeps the same surface. */
@Composable
expect fun VideoSurface(
    player: VideoPlayer,
    keepScreenOn: Boolean,
    modifier: Modifier = Modifier,
    keepContentOnPlayerReset: Boolean = false,
)

/**
 * True where the native video view hides Compose content drawn beneath it (iOS), so the poster
 * frame has to be drawn on top until [VideoPlayer.hasFrame].
 */
expect val videoSurfaceCoversPoster: Boolean
