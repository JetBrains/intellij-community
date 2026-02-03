// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.isMarkdownScratchFile
import org.intellij.plugins.markdown.lang.hasMarkdownType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownPreviewFileEditorProvider : WeighedFileEditorProvider(), AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) {
      return false
    }
    return file.hasMarkdownType() || isMarkdownScratchFile(project, file)
  }

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    return withContext(Dispatchers.EDT) {
      MarkdownPreviewFileEditor(project = project, file = file, document = document!!)
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return MarkdownPreviewFileEditor(project, file, FileDocumentManager.getInstance().getDocument(file)!!)
  }

  override fun getEditorTypeId(): String = "markdown-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
