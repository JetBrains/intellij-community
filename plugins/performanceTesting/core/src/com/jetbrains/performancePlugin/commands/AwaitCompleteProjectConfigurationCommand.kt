package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.awaitCompleteProjectConfiguration


private val LOG: Logger
  get() = logger<AwaitCompleteProjectConfigurationCommand>()


/**
 * Awaits until all configuration activities in a project are finished.
 */
class AwaitCompleteProjectConfigurationCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "awaitCompleteProjectConfiguration"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    context.project.awaitCompleteProjectConfiguration { str -> LOG.info(str) }
  }

  override fun getName(): String {
    return NAME
  }
}