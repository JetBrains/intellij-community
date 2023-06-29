package com.intellij.mermaid.preview

import com.intellij.mermaid.lang.isMermaidFile
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp

internal class MermaidEditorWithPreviewProvider: WeighedFileEditorProvider(), AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.isMermaidFile() && JBCefApp.isSupported()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val editor = PsiAwareTextEditorProvider().createEditor(project, file)
    check(editor is TextEditor) { "PsiAwareTextEditorProvider created editor which does not implement TextEditor" }
    val preview = MermaidPreviewEditor(project, file)
    return MermaidEditorWithPreview(editor, preview)
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
}
