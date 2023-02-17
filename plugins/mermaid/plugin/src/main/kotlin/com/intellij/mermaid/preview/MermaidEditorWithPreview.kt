package com.intellij.mermaid.preview

import com.intellij.openapi.fileEditor.*

internal class MermaidEditorWithPreview(
  editor: TextEditor,
  preview: MermaidPreviewEditor
): TextEditorWithPreview(editor, preview)
