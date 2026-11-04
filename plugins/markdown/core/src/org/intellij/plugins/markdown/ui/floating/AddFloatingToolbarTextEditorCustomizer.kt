// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.floating

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.editor.isMarkdownScratchFile
import org.intellij.plugins.markdown.lang.supportsMarkdown

internal class AddFloatingToolbarTextEditorCustomizer : TextEditorCustomizer {
  override fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
    if (!shouldAcceptEditor(textEditor) || AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")) {
      return
    }
    coroutineScope.launch(Dispatchers.EDT) {
      if (textEditor.editor.isDisposed) {
        return@launch
      }
      val toolbarScope = coroutineScope.childScope("MarkdownFloatingToolbar")
      val toolbar = MarkdownFloatingToolbar(editor = textEditor.editor, coroutineScope = toolbarScope)
      Disposer.register(textEditor, toolbar)
    }
  }

  private fun shouldAcceptEditor(editor: TextEditor): Boolean {
    return editor.editor.supportsMarkdown() || shouldAcceptScratchFile(editor)
  }

  private fun shouldAcceptScratchFile(editor: TextEditor): Boolean {
    val file = editor.file
    val project = editor.editor.project ?: return false
    return isMarkdownScratchFile(project, file)
  }
}
