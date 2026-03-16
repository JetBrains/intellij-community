package com.intellij.python.processOutput.frontend

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

internal fun Instant.formatFull(): String =
    fullFormatter.format(this.toJavaInstant())

internal fun Instant.formatTime(): String =
    timeFormatter.format(this.toJavaInstant())
