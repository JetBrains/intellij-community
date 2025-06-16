// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.MemoryCapture.Companion.capture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException

class ExitAppCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    internal const val PREFIX: @NonNls String = CMD_PREFIX + "exitApp"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    writeExitMetricsIfNeeded()

    val arguments = text.split(' ', limit = 2)

    var flags = ApplicationEx.SAVE or ApplicationEx.EXIT_CONFIRMED
    if (if (arguments.size > 1) arguments[1].toBoolean() else true) {
      flags = flags or ApplicationEx.FORCE_EXIT
    }

    withContext(Dispatchers.EDT) {
      WriteIntentReadAction.run {
        ApplicationManagerEx.getApplicationEx().exit(flags)
      }
    }
  }
}

internal fun writeExitMetricsIfNeeded() {
  System.getProperty("idea.log.exit.metrics.file")?.let {
    writeExitMetrics(it)
  }
}

private fun writeExitMetrics(path: String) {
  val capture = capture()

  val memory = MemoryMetrics(capture.usedMb, capture.maxMb, capture.metaspaceMb)
  val metrics = ExitMetrics(memory)
  try {
    ObjectMapper().writeValue(File(path), metrics)
  }
  catch (e: IOException) {
    println("Unable to write exit metrics from ${ExitAppCommand::class.java.getSimpleName()} ${e.message}")
  }
}
