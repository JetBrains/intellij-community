package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WaitForEDTQueueUnstuckCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "waitForEDTQueueUnstuck"
  }

  override fun getName(): String {
    return PREFIX
  }

  override suspend fun doExecute(context: PlaybackContext) {
    withContext(Dispatchers.EDT) {
      // do nothing
    }
  }
}
