// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.concurrency.awaitPromise
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.seconds

/**
 * %moveCaret text to find in file
 */
class MoveCaretCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "moveCaret"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val param = extractCommandArgument(PREFIX).trim()
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    assert(editor != null) {
      "editor is null"
    }
    val document = editor!!.document
    val index = document.charsSequence.indexOf(param)
    assert(index >= 0) {
      "text `${param}` not found"
    }
    val lineNumber = document.getLineNumber(index) + 1
    GoToCommand("${lineNumber} 1", 0)._execute(context).awaitPromise(60.seconds)
  }
}