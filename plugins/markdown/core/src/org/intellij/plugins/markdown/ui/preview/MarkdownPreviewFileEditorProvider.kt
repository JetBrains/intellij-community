package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.hasMarkdownType
import org.intellij.plugins.markdown.lang.isMarkdownLanguage

internal class MarkdownPreviewFileEditorProvider: WeighedFileEditorProvider() {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) {
      return false
    }
    return file.hasMarkdownType() || isMarkdownScratch(project, file)
  }

  private fun isMarkdownScratch(project: Project, file: VirtualFile): Boolean {
    if (!ScratchUtil.isScratch(file)) {
      return false
    }
    val language = LanguageUtil.getLanguageForPsi(project, file) ?: return false
    return language.isMarkdownLanguage()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return MarkdownPreviewFileEditor(project, file)
  }

  override fun getEditorTypeId(): String {
    return "markdown-preview-editor"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
  }
}
