// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.lensContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Command that waits for Code Vision to finish loading
 *
 * Usage: %waitForCodeVision timeout_in_seconds
 * Example: %waitForCodeVision 300
 */
class WaitForCodeVisionCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForCodeVision"
    val LOG: Logger = logger<WaitForCodeVisionCommand>()
  }


  override suspend fun doExecute(context: PlaybackContext) {
    val timeoutSeconds = (extractCommandArgument(PREFIX).trim().toIntOrNull() ?: 30).coerceAtLeast(30)
    val timeout = timeoutSeconds.seconds
    waitForCodeVisionToFinish(context, timeout)
  }

  override fun getName(): String {
    return PREFIX
  }

  private suspend fun waitForCodeVisionToFinish(context: PlaybackContext, timeout: Duration) {
    val project = context.project
    var codeVisionQueued = false

    try {
      withTimeout(timeout) {
        val editor = readAction {
          if (project.isDisposed) {
            LOG.warn("Project is disposed, cannot wait for Code Vision")
            return@readAction null
          }

          try {
            project.service<CodeVisionHost>()
          } catch (e: Exception) {
            LOG.warn("Failed to get CodeVisionHost", e)
            return@readAction null
          }

          val fileEditorManager = FileEditorManager.getInstance(project)
          fileEditorManager.selectedEditor ?: fileEditorManager.selectedEditorWithRemotes.firstOrNull()
        }

        if (editor == null) {
          LOG.warn("No text editor is selected, cannot wait for Code Vision")
          return@withTimeout
        }

        while (true) {
          val hasPending = readAction {
            if (!project.service<CodeVisionHost>().isInitialised) return@readAction false

            val lensContext = (editor as TextEditor).editor.lensContext
            lensContext?.hasAnyPendingLenses ?: false
          }

          if (!codeVisionQueued && hasPending) {
            codeVisionQueued = true
          }

          if (codeVisionQueued && !hasPending) {
            break
          }

          delay(300)
        }
      }
    } catch (e: TimeoutCancellationException) {
      if (!codeVisionQueued) {
        LOG.warn("Code Vision was not queued within $timeout - it might be disabled or not applicable for this file")
      }
      else {
        LOG.error("Waiting for Code Vision to finish took more than $timeout")
        throw e
      }
    }
  }
}
