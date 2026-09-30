// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Pair
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil

/**
 * Records a named mark: an instant event (a zero-duration span) that carries the current cumulative
 * JVM counters of [JvmUsageSnapshot] as attributes.
 *
 * Put two marks around a phase and subtract their counters to get the cost of that phase. The metrics
 * collector does this with `getDeltaMetricsBetweenMarks`.
 *
 * Syntax: %mark <name>
 */
class MarkCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "mark"
    const val SPAN_SCOPE: String = "mark"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val name = extractCommandArgument(PREFIX).trim()
    check(name.isNotEmpty()) { "%mark requires a name" }
    val attributes = JvmUsageSnapshot.capture().asAttributes()
      .map { Pair.create(it.first, it.second) }
      .toTypedArray()
    HighlightingTestUtil.storeProcessFinishedTime(SPAN_SCOPE, name, *attributes)
  }
}
