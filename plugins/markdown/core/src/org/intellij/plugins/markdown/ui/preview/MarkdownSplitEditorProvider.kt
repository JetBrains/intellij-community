package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSplitEditorProvider : TextEditorWithPreviewProvider(PsiAwareTextEditorProvider(), MarkdownPreviewFileEditorProvider()) {
  override fun createSplitEditor(firstEditor: FileEditor, secondEditor: FileEditor): FileEditor {
    require(firstEditor is TextEditor) { "Main editor should be TextEditor" }
    require(secondEditor is MarkdownPreviewFileEditor) { "Secondary editor should be MarkdownPreviewFileEditor" }
    return MarkdownEditorWithPreview(firstEditor, secondEditor)
  }
}
