// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.completion.BaseCompletionLookupArranger.getDefaultPresentation
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.*
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.seconds

/**
 * Command chooses a completion item by name. Completion popup should be opened.
 * Example: %chooseCompletionCommand {COMPLETION_NAME}
 */
class ChooseCompletionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "chooseCompletionCommand"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val completionName = extractCommandArgument(PREFIX).trim()
    try {
      withTimeout(5.seconds) {
        var selected = false
        while (!selected) {
          val lookup = getLookup(context)
          lookup?.items?.firstOrNull { getDefaultPresentation(it).itemText!! == completionName }?.also {
            withContext(Dispatchers.EDT) {
              ApplicationManager.getApplication().invokeAndWait {
                lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR, it)
                selected = true
              }
            }
          }
          yield()
        }
      }
    }
    catch (ignore: TimeoutCancellationException) {
      throw IllegalArgumentException("There is no completion with name $completionName." +
                                     " Actual items: ${
                                       getLookup(context)
                                         ?.items
                                         ?.joinToString("\n------\n") { getDefaultPresentation(it).itemText!! }
                                     }")
    }
  }

  private fun getLookup(context: PlaybackContext): LookupImpl? {
    val editor = FileEditorManager.getInstance(context.project).selectedTextEditor!!
    try {
      runBlocking {
        withTimeout(5.seconds) {
          LookupManager.getActiveLookup(editor) as LookupImpl? == null
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      throw IllegalStateException("There is no lookup after 5 seconds")
    }
    return LookupManager.getActiveLookup(editor) as? LookupImpl
  }
}
