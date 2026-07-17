// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of the source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview.accessor

import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PsiNavigateUtil
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import java.net.URI
import java.net.URISyntaxException

object MarkdownLinkOpenerUtil {
  private val logger = logger<MarkdownLinkOpenerUtil>()

  fun navigateToHeader(project: Project, headerInfo: MarkdownHeaderInfo) {
    createFileUri(headerInfo.filePath) ?: return
    val file = headerInfo.virtualFileId.virtualFile() ?: return
    val element = PsiUtilCore.getPsiFile(project, file).findElementAt(headerInfo.textOffset) ?: return
    val manager = FileEditorManager.getInstance(project)
    val openedEditor = manager.getEditorList(file).filterIsInstance<MarkdownEditorWithPreview>().firstOrNull()
    if (openedEditor == null) {
      val descriptor = OpenFileDescriptor(project, file, element.getTextOffset())
      manager.openEditor(descriptor, true)
      return
    }
    PsiNavigateUtil.navigate(element, true)
    (openedEditor.previewEditor as? MarkdownPreviewFileEditor)?.scrollToLine(openedEditor.editor, headerInfo.lineNumber)
  }

  private fun createFileUri(link: String?): URI? {
    try {
      return URI("file", null, link, null)
    }
    catch (exception: URISyntaxException) {
      logger.warn(exception)
      return null
    }
  }
}
