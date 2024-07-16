// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.currentOrDefaultProject
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSplitEditorProvider : TextEditorWithPreviewProvider(MarkdownPreviewFileEditorProvider()) {
  override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    require(secondEditor is MarkdownPreviewFileEditor) { "Secondary editor should be MarkdownPreviewFileEditor" }
    return MarkdownEditorWithPreview(firstEditor, secondEditor, currentOrDefaultProject(firstEditor.getEditor().getProject()))
  }
}
