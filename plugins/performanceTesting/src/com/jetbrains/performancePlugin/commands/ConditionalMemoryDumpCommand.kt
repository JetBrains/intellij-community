package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.MemoryDumpHelper

class ConditionalMemoryDumpCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "conditionalMemoryDumpCommand"
    const val PREFIX = CMD_PREFIX + NAME
    private val LOG = Logger.getInstance(ConditionalMemoryDumpCommand::class.java)
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val targetMessageCount = extractCommandArgument(PREFIX).toInt()
    var currentMessageCount = 0

    @Suppress("IncorrectParentDisposable")
    LowMemoryWatcher.register({
        currentMessageCount++
        if (currentMessageCount == targetMessageCount) {
          val memoryDumpPath = MemoryDumpCommand.getMemoryDumpPath()
          LOG.info("Dumping memory snapshot to: $memoryDumpPath")
          MemoryDumpHelper.captureMemoryDump(memoryDumpPath)
        }
      //we can't register a proper disposable since we wait till all commands are finished
      }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC, ApplicationManager.getApplication())
  }

  override fun getName(): String {
    return NAME
  }
}