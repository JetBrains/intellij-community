package com.intellij.python.processOutput.frontend

import com.intellij.openapi.util.NlsSafe
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
  .withZone(ZoneId.systemDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  .withZone(ZoneId.systemDefault())

internal fun Instant.formatFull(): String =
  fullFormatter.format(this.toJavaInstant())

internal fun Instant.formatTime(): @NlsSafe String =
  timeFormatter.format(this.toJavaInstant())

internal fun Duration.formatCompact(): @NlsSafe String {
  val ms = inWholeMilliseconds
 
  return when {
    ms < 1000 -> "$ms ms"
    ms < 60_000 -> "${ms / 1000} s"
    ms < 3_600_000 -> {
      val minutes = ms / 60_000
      val seconds = (ms % 60_000) / 1000
      if (seconds == 0L) "$minutes m" else "$minutes m $seconds s"
    }
    else -> {
      val hours = ms / 3_600_000
      val minutes = (ms % 3_600_000) / 60_000
      if (minutes == 0L) "$hours h" else "$hours h $minutes m"
    }
  }
}
