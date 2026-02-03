// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.MemoryDumpHelper

class ConditionalMemoryDumpCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "conditionalMemoryDumpCommand"
    const val PREFIX = CMD_PREFIX + NAME
    const val WITH_ERROR_TEXT = "WITH_ERROR_MESSAGE"
    private val LOG = logger<ConditionalMemoryDumpCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val params = extractCommandArgument(PREFIX).split("\\s+".toRegex())
    val targetMessageCount = params[0].toInt()
    var currentMessageCount = 0
    val withErrorMessages = params.contains(WITH_ERROR_TEXT)

    @Suppress("IncorrectParentDisposable")
    LowMemoryWatcher.register({
        currentMessageCount++
        if (currentMessageCount == targetMessageCount) {
          val memoryDumpPath = MemoryDumpCommand.getMemoryDumpPath()
          LOG.info("Dumping memory snapshot to: $memoryDumpPath")
          MemoryDumpHelper.captureMemoryDump(memoryDumpPath)
          if (withErrorMessages) {
            LOG.error("Got low memory signal, memory dump attached")
          }
        }
      //we can't register a proper disposable since we wait till all commands are finished
      }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC, ApplicationManager.getApplication())
  }

  override fun getName(): String {
    return NAME
  }
}