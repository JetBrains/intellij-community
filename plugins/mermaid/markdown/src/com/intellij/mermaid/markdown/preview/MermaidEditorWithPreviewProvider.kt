// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.mermaid.lang.isMermaidFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.application

internal class MermaidEditorWithPreviewProvider: WeighedFileEditorProvider(), AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.isMermaidFile() && JBCefApp.isSupported()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val editor = PsiAwareTextEditorProvider().createEditor(project, file)
    check(editor is TextEditor) { "PsiAwareTextEditorProvider created editor which does not implement TextEditor" }
    try {
      val preview = MermaidPreviewEditor(project, file)
      return MermaidEditorWithPreview(editor, preview)
    } catch (exception: Exception) {
      thisLogger().productionWarning("Failed to create preview editor", exception)
    }
    thisLogger().warn("Using default text editor as a fallback")
    return editor
  }

  override fun getEditorTypeId(): String {
    return "MermaidEditorWithPreview"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    return object: AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return createEditor(project, file)
      }
    }
  }

  private fun Logger.productionWarning(message: String, throwable: Throwable) {
    when {
      application.isUnitTestMode -> error(message, throwable)
      else -> warn(message, throwable)
    }
  }
}
