// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import org.jetbrains.annotations.NonNls

/**
 * Assert the current file in editor.
 *
 * Syntax: `%assertCurrentFile <file name>`
 *
 * Example: `%assertCurrentFile AssertCurrentFileCommand.kt`
 */
class AssertCurrentFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "assertCurrentFile"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val fileName = extractCommandArgument(PREFIX).split(" ").filterNot { it.trim() == "" }.singleOrNull()
    if (fileName == null) {
      throw IllegalArgumentException("File name is not provided")
    }

    readAction {
      val editor = checkNotNull(FileEditorManager.getInstance(context.project).selectedTextEditor)
      val currentFileName = editor.virtualFile.name
      if (fileName != currentFileName) {
        throw Exception("Current file name is $currentFileName, expected $fileName")
      }
    }
  }

  override fun getName(): String = NAME
}