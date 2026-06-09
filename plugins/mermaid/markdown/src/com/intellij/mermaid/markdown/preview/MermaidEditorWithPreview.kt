// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview

internal class MermaidEditorWithPreview(
  editor: TextEditor,
  preview: MermaidPreviewEditor
): TextEditorWithPreview(editor, preview)
