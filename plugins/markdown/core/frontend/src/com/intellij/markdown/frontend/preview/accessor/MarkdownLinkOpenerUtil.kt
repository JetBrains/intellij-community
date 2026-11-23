// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of the source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview.accessor

import com.intellij.ide.BrowserUtil
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PsiNavigateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

  /**
   * Navigates to the header. The EDT does not wait for the editor when the file is not open.
   */
  @ApiStatus.Internal
  suspend fun navigateToHeaderAsync(project: Project, headerInfo: MarkdownHeaderInfo) {
    val file = headerInfo.virtualFileId.virtualFile() ?: return
    val isDone = withContext(Dispatchers.EDT) {
      val element = PsiUtilCore.getPsiFile(project, file).findElementAt(headerInfo.textOffset)
      element == null || navigateInOpenEditor(project, file, element, headerInfo)
    }
    if (isDone) {
      return
    }
    navigateToOffset(project, file) {
      PsiUtilCore.getPsiFile(project, file).findElementAt(headerInfo.textOffset)?.textOffset
    }
  }

  /**
   * Opens [file] with the caret at the start of the 0-based [line]. The EDT does not wait for the editor.
   */
  internal suspend fun navigateToLine(project: Project, file: VirtualFile, line: Int) {
    navigateToOffset(project, file) {
      val document = FileDocumentManager.getInstance().getDocument(file)
      // a file without a document still opens, and the caret stays where it is
      if (document == null || document.lineCount == 0) -1 else document.getLineStartOffset(line.coerceAtMost(document.lineCount - 1))
    }
  }

  /**
   * Opens [file] through [NavigationService]. The [offset] runs in a read action, and `null` means that no navigation happens.
   */
  private suspend fun navigateToOffset(project: Project, file: VirtualFile, offset: () -> Int?) {
    NavigationService.getInstance(project).navigateRequests(NavigationOptions.requestFocus()) {
      readAction {
        val targetOffset = offset() ?: return@readAction emptyList()
        listOfNotNull(NavigationRequest.sourceNavigationRequest(project, file, targetOffset))
      }
    }
  }

  /**
   * Navigates in an editor of [file] that is open already, so the navigation waits for no editor.
   */
  private fun navigateInOpenEditor(project: Project, file: VirtualFile, element: PsiElement, headerInfo: MarkdownHeaderInfo): Boolean {
    val manager = FileEditorManager.getInstance(project)
    val openedEditor = manager.getSelectedEditor(file) as? MarkdownHeaderNavigationHandler
                       ?: manager.getEditorList(file).filterIsInstance<MarkdownHeaderNavigationHandler>().firstOrNull()
                       ?: return false
    openedEditor.navigateToHeader(headerInfo.textOffset, headerInfo.lineNumber)
    PsiNavigateUtil.navigate(element, true)
    return true
  }
}
