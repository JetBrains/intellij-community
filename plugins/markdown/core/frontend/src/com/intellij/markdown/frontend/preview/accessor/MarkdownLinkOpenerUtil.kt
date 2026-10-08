// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of the source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview.accessor

import com.intellij.ide.BrowserUtil
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PsiNavigateUtil
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.ui.preview.MarkdownHeaderNavigationHandler
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URISyntaxException

object MarkdownLinkOpenerUtil {
  private val logger = logger<MarkdownLinkOpenerUtil>()

  /**
   * Creates a URI from a Markdown link and encodes hash characters after the fragment delimiter.
   */
  @ApiStatus.Internal
  fun createUri(link: String): URI? {
    val normalizedLink = encodeAdditionalHashesInFragment(link)
    return try {
      when {
        BrowserUtil.isAbsoluteURL(normalizedLink) -> URI(normalizedLink)
        else -> URI("http://$normalizedLink")
      }
    }
    catch (exception: URISyntaxException) {
      logger.warn(exception)
      null
    }
  }

  private fun encodeAdditionalHashesInFragment(link: String): String {
    val fragmentStart = link.indexOf('#')
    if (fragmentStart < 0) {
      return link
    }
    return link.substring(0, fragmentStart + 1) + link.substring(fragmentStart + 1).replace("#", "%23")
  }

  fun navigateToHeader(project: Project, headerInfo: MarkdownHeaderInfo) {
    createFileUri(headerInfo.filePath) ?: return
    val file = headerInfo.virtualFileId.virtualFile() ?: return
    val element = PsiUtilCore.getPsiFile(project, file).findElementAt(headerInfo.textOffset) ?: return
    val manager = FileEditorManager.getInstance(project)
    val openedEditor = manager.getSelectedEditor(file) as? MarkdownHeaderNavigationHandler
                       ?: manager.getEditorList(file).filterIsInstance<MarkdownHeaderNavigationHandler>().firstOrNull()
    if (openedEditor == null) {
      val descriptor = OpenFileDescriptor(project, file, element.getTextOffset())
      manager.openEditor(descriptor, true)
      return
    }
    openedEditor.navigateToHeader(headerInfo.textOffset, headerInfo.lineNumber)
    PsiNavigateUtil.navigate(element, true)
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
