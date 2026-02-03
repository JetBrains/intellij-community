// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class CloseLookupCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}closeLookup"

    /**
     * Closes the currently active lookup in the selected text editor, if one exists.
     *
     * @return A boolean value indicating whether the active lookup was successfully closed.
     *         Returns `true` if an active lookup was found and closed; `false` otherwise.
     */
    @JvmStatic
    suspend fun closeLookup(project: Project): Boolean =
      FileEditorManager.getInstance(project).getSelectedTextEditor().let { editor ->
        return withContext(Dispatchers.EDT) {
          val activeLookup = LookupManager.getActiveLookup(editor)
          if (activeLookup != null) {
            activeLookup.hideLookup(true)
            return@withContext true
          }
          return@withContext false
        }
      }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    while (closeLookup(context.project)) delay(1000)
  }

}