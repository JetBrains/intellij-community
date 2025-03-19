package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter

class WaitForInitialRefreshCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForInitialRefresh"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    context.project.serviceAsync<InitialVfsRefreshService>().awaitInitialVfsRefreshFinished()
  }
}
