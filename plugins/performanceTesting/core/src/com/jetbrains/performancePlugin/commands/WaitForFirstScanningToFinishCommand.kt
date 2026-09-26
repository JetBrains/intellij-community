// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.indexing.isFirstProjectScanningPerformed
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class WaitForFirstScanningToFinishCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForFirstScanningToFinish"
    private val LOG = logger<WaitForFirstScanningToFinishCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val time = measureTime {
      while (!isFirstProjectScanningPerformed(project)) {
        delay(10.milliseconds)
      }
    }
    LOG.info("%waitForScanningToFinish finished. Waiting took $time")
  }
}
