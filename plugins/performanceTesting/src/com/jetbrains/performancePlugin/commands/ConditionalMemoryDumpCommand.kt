package com.jetbrains.performancePlugin.commands

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
    var memoryDumpCollected = false
    LowMemoryWatcher.register(
      {
        currentMessageCount++
        if (currentMessageCount == targetMessageCount && !memoryDumpCollected) {
          memoryDumpCollected = true
          val memoryDumpPath = MemoryDumpCommand.getMemoryDumpPath()
          LOG.info("Dumping memory snapshot to: $memoryDumpPath")
          MemoryDumpHelper.captureMemoryDumpZipped(memoryDumpPath)
        }
      }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)
  }

  override fun getName(): String {
    return NAME
  }
}