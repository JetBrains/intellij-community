// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.floating

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.intellij.plugins.markdown.editor.isMarkdownScratchFile
import org.intellij.plugins.markdown.lang.hasMarkdownType

internal class AddFloatingToolbarTextEditorCustomizer : TextEditorCustomizer {
  override fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
    if (!shouldAcceptEditor(textEditor) || AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")) {
      return
    }
    // See FloatingCodeToolbarEditorCustomizer: the toolbar holds its scope until disposed.
    val toolbarScope = coroutineScope.childScope("MarkdownFloatingToolbar")
    var registered = false
    try {
      val toolbar = MarkdownFloatingToolbar(editor = textEditor.editor, coroutineScope = toolbarScope)
      registered = Disposer.tryRegister(textEditor, toolbar)
      if (!registered) {
        Disposer.dispose(toolbar)
      }
    }
    finally {
      if (!registered) {
        toolbarScope.cancel()
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
