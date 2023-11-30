package org.intellij.plugins.markdown.ui.floating

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.editor.isMarkdownScratchFile
import org.intellij.plugins.markdown.lang.hasMarkdownType
import org.intellij.plugins.markdown.util.MarkdownPluginScope

private class AddFloatingToolbarTextEditorCustomizer: TextEditorCustomizer {
  override fun customize(textEditor: TextEditor) {
    val project = textEditor.editor.project ?: return
    if (shouldAcceptEditor(textEditor) && !AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")) {
      val coroutineScope = MarkdownPluginScope.createChildScope(project)
      val toolbar = MarkdownFloatingToolbar(textEditor.editor, coroutineScope)
      Disposer.register(textEditor, toolbar)
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
