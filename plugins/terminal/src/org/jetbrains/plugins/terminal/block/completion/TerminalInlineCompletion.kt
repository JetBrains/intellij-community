// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.codeInsight.inline.completion.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class TerminalInlineCompletion(private val scope: CoroutineScope) {
  fun install(editor: EditorEx) {
    InlineCompletion.install(editor, scope)
  }

  companion object {
    fun getInstance(project: Project): TerminalInlineCompletion = project.service()
  }
}