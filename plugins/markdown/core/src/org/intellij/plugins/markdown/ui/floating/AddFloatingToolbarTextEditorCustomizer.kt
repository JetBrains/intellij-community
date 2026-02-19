// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.floating

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.coroutineScope
import org.intellij.plugins.markdown.editor.isMarkdownScratchFile
import org.intellij.plugins.markdown.lang.hasMarkdownType

internal class AddFloatingToolbarTextEditorCustomizer: TextEditorCustomizer {
  override suspend fun execute(textEditor: TextEditor) {
    if (shouldAcceptEditor(textEditor) && !AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")) {
      coroutineScope {
        val toolbar = MarkdownFloatingToolbar(editor = textEditor.editor, coroutineScope = this)
        Disposer.register(textEditor, toolbar)
      }
    }
  }

  private fun shouldAcceptEditor(editor: TextEditor): Boolean {
    val file = editor.file
    return file.hasMarkdownType() || shouldAcceptScratchFile(editor)
  }

  private fun shouldAcceptScratchFile(editor: TextEditor): Boolean {
    val file = editor.file
    val project = editor.editor.project ?: return false
    return isMarkdownScratchFile(project, file)
  }
}
