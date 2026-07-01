// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.mermaid.lang.isMermaidFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MermaidPreviewFileEditorProvider : WeighedFileEditorProvider(), AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.isMermaidFile() && JBCefApp.isSupported()
  }

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    return withContext(Dispatchers.EDT) {
      MermaidPreviewEditor(project, file)
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return MermaidPreviewEditor(project, file)
  }

  override fun getEditorTypeId(): String = "mermaid-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
