// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.configuration.ConfigurationResult
import com.intellij.openapi.project.configuration.awaitCompleteProjectConfiguration
import com.intellij.openapi.ui.playback.PlaybackContext


private val LOG: Logger
  get() = logger<AwaitCompleteProjectConfigurationCommand>()


/**
 * Awaits until all configuration activities in a project are finished.
 */
class AwaitCompleteProjectConfigurationCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "awaitCompleteProjectConfiguration"
    const val PREFIX = "$CMD_PREFIX$NAME"

    suspend fun awaitCompleteProjectConfiguration(project: Project) {
        val result = project.awaitCompleteProjectConfiguration { str -> LOG.info(str) }
        if (result is ConfigurationResult.Failure) {
          LOG.error(result.message)
        }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    awaitCompleteProjectConfiguration(context.project)
  }

  override fun getName(): String {
    return NAME
  }
}