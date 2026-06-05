package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview

internal class MermaidEditorWithPreview(
  editor: TextEditor,
  preview: MermaidPreviewEditor
): TextEditorWithPreview(editor, preview)
