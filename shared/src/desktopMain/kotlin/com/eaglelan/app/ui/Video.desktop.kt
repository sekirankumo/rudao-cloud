package com.eaglelan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.math.min
import kotlin.math.roundToInt

/** libVLC, loaded once: the copy bundled with the app (resources/windows/vlc), else an installed VLC. */
internal object Vlc {
    /** All libVLC calls run here, never on the UI thread or inside a libVLC callback. */
    val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "vlc").apply { isDaemon = true } }

    val factory: MediaPlayerFactory? by lazy {
        val bundled = System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") }
        if (bundled != null && File(bundled, "libvlc.dll").exists()) {
            System.setProperty("jna.library.path", bundled.absolutePath)
        }
        if (!NativeDiscovery().discover()) null
        else MediaPlayerFactory("--quiet", "--no-video-title-show", "--no-osd", "--no-snapshot-preview")
    }

    fun release() {
        worker.submit { factory?.release() }
    }
}

/** Frames are scaled by libVLC to fit this box; more is wasted copying. */
private const val MAX_FRAME = 1920

class VlcVideoPlayer(private val kind: PlayerKind) : VideoPlayer {
    private val mp = Vlc.factory?.mediaPlayers()?.newEmbeddedMediaPlayer()

    // ---- state mirrored from libVLC events (read on the UI thread) ----------
    @Volatile private var opened = false
    @Volatile private var playing = false
    @Volatile private var cache = 0f
    @Volatile private var ended = false
    @Volatile private var error = mp == null
    @Volatile private var time = 0L
    @Volatile private var length = 0L
    @Volatile private var released = false
    private var pendingSeek: Long? = null
    private var url: String? = null
    private var speed = 1f
    private var vol = 1f
    private var wantPlay = false

    // ---- frames: libVLC renders into [buffer]; the UI draws [frame] -----------
    internal var frame: Bitmap? = null
        private set
    internal var frameVersion by mutableIntStateOf(0)
        private set
    private var info: ImageInfo? = null
    private var bytes = ByteArray(0)

    init {
        mp?.videoSurface()?.set(
            Vlc.factory!!.videoSurfaces().newVideoSurface(
                object : BufferFormatCallback {
                    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                        val scale = min(1.0, MAX_FRAME.toDouble() / maxOf(sourceWidth, sourceHeight))
                        val w = (sourceWidth * scale).roundToInt().coerceAtLeast(2)
                        val h = (sourceHeight * scale).roundToInt().coerceAtLeast(2)
                        info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                        bytes = ByteArray(w * h * 4)
                        return RV32BufferFormat(w, h)
                    }

                    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
                },
                object : RenderCallback {
                    override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                        val i = info ?: return
                        val src = nativeBuffers[0]
                        src.rewind()
                        val copy = bytes
                        if (src.remaining() < copy.size) return
                        src.get(copy)
                        SwingUtilities.invokeLater {
                            if (released) return@invokeLater
                            val bmp = frame?.takeIf { it.width == i.width && it.height == i.height } ?: Bitmap()
                            bmp.installPixels(i, copy, i.width * 4)
                            frame = bmp
                            frameVersion++
                        }
                    }
                },
                true,
            )
        )
        mp?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) {
                opened = false
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                length = newLength
                markOpened()
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                time = newTime
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                cache = newCache
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                playing = true
                cache = 100f
                markOpened()
                Vlc.worker.submit { if (!released) mp.audio().setVolume((vol * 100).roundToInt()) }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                playing = false
                markOpened()
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                playing = false
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                playing = false
                if (kind == PlayerKind.FULL) {
                    // loop, like ExoPlayer's REPEAT_MODE_ONE: start the same media again
                    val again = url
                    Vlc.worker.submit { if (!released && again != null) start(again, 0L, play = true) }
                } else {
                    ended = true
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                error = true
            }
        })
    }

    /** The first event that proves the input is open; seeks issued before it are applied now. */
    private fun markOpened() {
        if (opened) return
        opened = true
        Vlc.worker.submit {
            if (released) return@submit
            mp!!.audio().setVolume((vol * 100).roundToInt())
            if (speed != 1f) mp.controls().setRate(speed)
        }
    }

    /** On [Vlc.worker]: opens [mrl] paused at [startMs] (":start-paused"), then plays if wanted. */
    private fun start(mrl: String, startMs: Long, play: Boolean) {
        val m = mp ?: return
        val caching = if (kind == PlayerKind.PREVIEW) 1000 else 1500
        m.media().play(mrl, ":start-paused", ":start-time=${startMs / 1000.0}", ":network-caching=$caching")
        if (play) m.controls().play()
    }

    override var volume: Float
        get() = vol
        set(v) {
            vol = v
            Vlc.worker.submit { if (!released && opened) mp?.audio()?.setVolume((v * 100).roundToInt()) }
        }

    override var playWhenReady: Boolean
        get() = wantPlay
        set(v) {
            if (v == wantPlay) return
            wantPlay = v
            Vlc.worker.submit {
                if (released) return@submit
                if (v) mp?.controls()?.play() else mp?.controls()?.setPause(true)
            }
        }

    override val isPlaying get() = playing
    override val isBuffering get() = !error && url != null && (!opened || (wantPlay && cache < 100f))
    override val isEnded get() = ended
    override val hasError get() = error
    override val hasFrame get() = frame != null
    override val currentPosition get() = time
    override val duration get() = length
    // libVLC doesn't report how far ahead it has buffered.
    override val bufferedPosition get() = time

    override fun setUrl(url: String) {
        this.url = url
        opened = false
        ended = false
        error = mp == null
        time = 0L
        length = 0L
        pendingSeek = null
        Vlc.worker.submit {
            if (released) return@submit
            // A seek right after setUrl (the usual case) becomes the start position.
            val startAt = pendingSeek ?: 0L
            pendingSeek = null
            start(url, startAt, play = wantPlay)
        }
    }

    override fun seekTo(positionMs: Long) {
        ended = false
        time = positionMs
        if (!opened) {
            pendingSeek = positionMs
        }
        Vlc.worker.submit {
            if (released) return@submit
            if (opened) mp?.controls()?.setTime(positionMs)
        }
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        Vlc.worker.submit { if (!released) mp?.controls()?.setRate(speed) }
    }

    override fun release() {
        released = true
        frame = null
        Vlc.worker.submit {
            mp?.controls()?.stop()
            mp?.release()
        }
    }
}

@Composable
actual fun VideoSurface(player: VideoPlayer, keepScreenOn: Boolean, modifier: Modifier, keepContentOnPlayerReset: Boolean) {
    val p = player as VlcVideoPlayer
    Canvas(modifier) {
        p.frameVersion          // redraw on every new frame
        val bmp = p.frame ?: return@Canvas
        // letterbox, like ExoPlayer's RESIZE_MODE_FIT
        val scale = min(size.width / bmp.width, size.height / bmp.height)
        val w = (bmp.width * scale).roundToInt()
        val h = (bmp.height * scale).roundToInt()
        drawImage(
            bmp.asComposeImageBitmap(),
            dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium,
        )
    }
}

actual val videoSurfaceCoversPoster = false
