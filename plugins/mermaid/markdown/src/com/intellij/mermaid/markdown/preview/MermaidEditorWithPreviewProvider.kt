// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MermaidEditorWithPreviewProvider : TextEditorWithPreviewProvider(MermaidPreviewFileEditorProvider()) {
  override suspend fun createSplitEditorAsync(project: Project, firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    return withContext(Dispatchers.EDT) {
      createMermaidSplitEditor(firstEditor, secondEditor)
    }
  }

  override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    return createMermaidSplitEditor(firstEditor, secondEditor)
  }

  private fun createMermaidSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    require(secondEditor is MermaidPreviewEditor) { "Secondary editor should be MermaidPreviewEditor" }
    return MermaidEditorWithPreview(firstEditor, secondEditor)
  }
}
