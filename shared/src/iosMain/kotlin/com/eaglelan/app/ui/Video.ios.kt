package com.eaglelan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
// Many AVPlayer members are Objective-C category extensions in Kotlin; star imports pick them all up.
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSValue
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObjectProtocol

@OptIn(ExperimentalForeignApi::class)
private fun CValue<CMTime>.millis(): Long {
    val s = CMTimeGetSeconds(this)
    return if (s.isNaN() || s.isInfinite()) 0L else (s * 1000).toLong()
}

@OptIn(ExperimentalForeignApi::class)
class AvVideoPlayer(private val kind: PlayerKind) : VideoPlayer {
    val player = AVPlayer()
    /** Set by [VideoSurface] while this player is the one shown. */
    internal var layer: AVPlayerLayer? = null
    private var endObserver: NSObjectProtocol? = null
    private var ended = false
    private var wantPlay = false
    private var speed = 1f

    override var volume: Float
        get() = player.volume
        set(v) {
            player.volume = v
        }

    override var playWhenReady: Boolean
        get() = wantPlay
        set(v) {
            if (v == wantPlay) return
            wantPlay = v
            if (v) player.rate = speed else player.pause()
        }

    override val isPlaying get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying
    override val isBuffering: Boolean
        get() {
            val item = player.currentItem ?: return false
            return item.status == AVPlayerItemStatusUnknown ||
                player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
        }
    override val isEnded get() = ended
    override val hasError get() = player.currentItem?.status == AVPlayerItemStatusFailed
    override val hasFrame get() = layer?.readyForDisplay == true
    override val currentPosition get() = player.currentTime().millis()
    override val duration get() = player.currentItem?.duration?.millis() ?: 0L
    override val bufferedPosition: Long
        get() {
            val item = player.currentItem ?: return 0L
            val now = currentPosition / 1000.0
            var end = 0.0
            item.loadedTimeRanges.forEach { v ->
                (v as NSValue).CMTimeRangeValue.useContents {
                    if (start.timescale == 0 || duration.timescale == 0) return@useContents
                    val s = start.value.toDouble() / start.timescale
                    val e = s + duration.value.toDouble() / duration.timescale
                    if (now >= s - 0.5 && e > end) end = e
                }
            }
            return (end * 1000).toLong()
        }

    override fun setUrl(url: String) {
        removeEndObserver()
        ended = false
        val item = AVPlayerItem(uRL = NSURL(string = url))
        // 随机速览 only needs a few seconds ahead
        if (kind == PlayerKind.PREVIEW) item.preferredForwardBufferDuration = 15.0
        player.replaceCurrentItemWithPlayerItem(item)
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue,
        ) { _ ->
            if (kind == PlayerKind.FULL) {
                // loop, like ExoPlayer's REPEAT_MODE_ONE
                player.seekToTime(CMTimeMake(0, 1))
                if (wantPlay) player.rate = speed
            } else {
                ended = true
            }
        }
        if (wantPlay) player.rate = speed
    }

    override fun seekTo(positionMs: Long) {
        ended = false
        val t = CMTimeMakeWithSeconds(positionMs / 1000.0, 600)
        // FULL seeks exactly; 随机速览 lands on the nearest keyframe, which is much faster.
        if (kind == PlayerKind.FULL) player.seekToTime(t, CMTimeMake(0, 1), CMTimeMake(0, 1))
        else player.seekToTime(t)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        if (wantPlay) player.rate = speed
    }

    override fun release() {
        player.pause()
        removeEndObserver()
        player.replaceCurrentItemWithPlayerItem(null)
        layer = null
    }

    private fun removeEndObserver() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PlayerHostView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    val playerLayer = AVPlayerLayer().apply { videoGravity = AVLayerVideoGravityResizeAspect }

    init {
        backgroundColor = UIColor.clearColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun VideoSurface(player: VideoPlayer, keepScreenOn: Boolean, modifier: Modifier, keepContentOnPlayerReset: Boolean) {
    val p = player as AvVideoPlayer
    UIKitView(
        factory = { PlayerHostView() },
        modifier = modifier,
        update = { view ->
            if (view.playerLayer.player != p.player) view.playerLayer.player = p.player
            p.layer = view.playerLayer
        },
        // touches stay with Compose (tap / double-tap / swipe layers above)
        properties = UIKitInteropProperties(interactionMode = null),
    )
    DisposableEffect(keepScreenOn) {
        UIApplication.sharedApplication.idleTimerDisabled = keepScreenOn
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

actual val videoSurfaceCoversPoster = true
