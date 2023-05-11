package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.MemoryDumpHelper

class ConditionalMemoryDumpCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line), Disposable {
  companion object {
    const val NAME = "conditionalMemoryDumpCommand"
    const val PREFIX = CMD_PREFIX + NAME
    private val LOG = Logger.getInstance(ConditionalMemoryDumpCommand::class.java)
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val targetMessageCount = extractCommandArgument(PREFIX).toInt()
    var currentMessageCount = 0
    LowMemoryWatcher.register({
        currentMessageCount++
        if (currentMessageCount == targetMessageCount) {
          val memoryDumpPath = MemoryDumpCommand.getMemoryDumpPath()
          LOG.info("Dumping memory snapshot to: $memoryDumpPath")
          MemoryDumpHelper.captureMemoryDumpZipped(memoryDumpPath)
          Disposer.dispose(this)
        }
      }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC, this)
  }

  override fun getName(): String {
    return NAME
  }

  override fun dispose() {
  }
}