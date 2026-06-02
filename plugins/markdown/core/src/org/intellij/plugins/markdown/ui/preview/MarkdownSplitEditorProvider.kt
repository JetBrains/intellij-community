// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSplitEditorProvider : TextEditorWithPreviewProvider(MarkdownPreviewFileEditorProvider()) {

  override suspend fun createSplitEditorAsync(project: Project, firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    val settings = MarkdownSettings.getInstanceAsync(project)
    return withContext(Dispatchers.EDT) {
      createMarkdownSplitEditor(firstEditor, secondEditor, settings)
    }
  }

  override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    return createMarkdownSplitEditor(firstEditor, secondEditor, MarkdownSettings.getInstance(getProject(firstEditor)))
  }

  private fun createMarkdownSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor, settings: MarkdownSettings): FileEditor {
    require(secondEditor is MarkdownPreviewFileEditor) { "Secondary editor should be MarkdownPreviewFileEditor" }
    return MarkdownEditorWithPreview(firstEditor, secondEditor, getProject(firstEditor), settings)
  }

  private fun getProject(firstEditor: TextEditor): Project = currentOrDefaultProject(firstEditor.getEditor().getProject())
}
