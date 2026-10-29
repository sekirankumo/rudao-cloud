package com.eaglelan.app.data

import com.eaglelan.app.ui.format1
import kotlinx.serialization.Serializable
import kotlin.math.pow

/** EBU R128 loudness of one video, measured on the computer: integrated, plus one value per 3 s block. */
@Serializable
data class LoudnessDto(val i: Double = SILENCE_LUFS, val b: List<Double> = emptyList(), val none: Boolean = false)

const val SILENCE_LUFS = -99.0

enum class LoudMode(val label: String) {
    STANDARD("响度：标准"),
    DOWN("响度：只压不抬"),
    OFF("响度：关");

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/**
 * Same rules as the 随机速览 Eagle plugin:
 * - a clip gets one fixed gain from the loudest 3 s block it covers (a loud moment inside a quiet clip is
 *   never boosted); a whole video gets one gain from its integrated loudness, keeping its own dynamics;
 * - target -26 LUFS (near the loud end of this library), boosting at most +8 dB so whispers stay quiet;
 * - gain is applied through the player volume. In STANDARD the base volume is -8 dB, so a +8 dB boost
 *   still stays at or below unity — nothing can clip.
 */
object Loudness {
    private const val TARGET = -26.0
    private const val MAX_BOOST = 8.0
    private const val BLOCK_MS = 3000L

    /** dB to apply, or null when unknown / switched off. Pass startMs = null for a whole video. */
    fun gainDb(d: LoudnessDto?, startMs: Long?, lengthMs: Long, mode: LoudMode): Double? {
        if (mode == LoudMode.OFF || d == null || d.none) return null
        val level = if (startMs == null) d.i else {
            val i0 = (startMs / BLOCK_MS).toInt().coerceAtLeast(0)
            val i1 = (((startMs + lengthMs) + BLOCK_MS - 1) / BLOCK_MS - 1).toInt().coerceAtMost(d.b.lastIndex)
            if (i0 > i1) SILENCE_LUFS else d.b.subList(i0, i1 + 1).maxOrNull() ?: SILENCE_LUFS
        }
        if (level <= SILENCE_LUFS) return 0.0                       // silence: never boost
        val cap = if (mode == LoudMode.STANDARD) MAX_BOOST else 0.0
        return (TARGET - level).coerceIn(-30.0, cap)
    }

    fun volume(gainDb: Double?, mode: LoudMode): Float {
        val base = if (mode == LoudMode.STANDARD) -MAX_BOOST else 0.0
        val db = base + (gainDb ?: 0.0)
        return 10.0.pow(db / 20).toFloat().coerceIn(0f, 1f)
    }

    fun label(gainDb: Double?, mode: LoudMode): String = when {
        mode == LoudMode.OFF -> ""
        gainDb == null -> "响度未分析"
        else -> "响度 " + (if (gainDb >= 0) "+" else "") + format1(gainDb) + " dB"
    }
}
