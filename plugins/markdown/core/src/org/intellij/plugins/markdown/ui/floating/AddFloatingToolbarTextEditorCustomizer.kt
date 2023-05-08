package org.intellij.plugins.markdown.ui.floating

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownLanguage

private class AddFloatingToolbarTextEditorCustomizer: TextEditorCustomizer {
  override fun customize(textEditor: TextEditor) {
    if (shouldAcceptEditor(textEditor) && !AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")) {
      val toolbar = FloatingToolbar(textEditor.editor, "Markdown.Toolbar.Floating")
      Disposer.register(textEditor, toolbar)
    }
  }

  private fun shouldAcceptEditor(editor: TextEditor): Boolean {
    val file = editor.file
    return file.fileType == MarkdownFileType.INSTANCE || shouldAcceptScratchFile(editor)
  }

  private fun shouldAcceptScratchFile(editor: TextEditor): Boolean {
    val file = editor.file
    val project = editor.editor.project ?: return false
    return ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file, file.fileType) == MarkdownLanguage.INSTANCE
  }
}
