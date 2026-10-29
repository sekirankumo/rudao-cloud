package com.eaglelan.app.ui

import kotlin.math.abs
import kotlin.math.roundToLong

fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
}

fun formatMillis(ms: Long) = formatDuration(ms / 1000.0)

fun formatSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else "${format1(v)} ${units[i]}"
}

/** Like String.format("%.1f") (not available in common code). */
fun format1(v: Double): String {
    val tenths = abs(v * 10).roundToLong()
    return (if (v < 0) "-" else "") + "${tenths / 10}.${tenths % 10}"
}

private fun pad2(n: Long) = n.toString().padStart(2, '0')
