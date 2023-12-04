package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.delay
import org.jetbrains.annotations.NonNls

class SleepCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "sleep"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val timeOutMs = extractCommandArgument(PREFIX).toLong()
    delay(timeOutMs)
  }
}