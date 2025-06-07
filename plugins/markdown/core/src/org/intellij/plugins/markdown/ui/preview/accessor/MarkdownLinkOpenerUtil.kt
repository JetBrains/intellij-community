// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.accessor

import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.UriUtil
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

object MarkdownLinkOpenerUtil {
  private val  logger = logger<MarkdownLinkOpenerUtil>()

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
    (openedEditor.previewEditor as? MarkdownPreviewFileEditor)
      ?.scrollToLine(openedEditor.editor, headerInfo.lineNumber)
  }

  fun collectHeaders(project: Project, anchor: String, targetFile: VirtualFile): List<MarkdownHeaderInfo>? {
    return runReadAction {
      if (DumbService.isDumb(project)) {
        return@runReadAction emptyList()
      }
      val scope = when (val file = PsiManager.getInstance(project).findFile(targetFile)) {
        null -> GlobalSearchScope.EMPTY_SCOPE
        else -> GlobalSearchScope.fileScope(file)
      }
      return@runReadAction HeaderAnchorIndex.collectHeaders(project, scope, anchor).map(MarkdownHeaderMapper::map)
    }
  }

  fun URI.findVirtualFile(): VirtualFile? {
    val actualPath = when {
      SystemInfo.isWindows -> UriUtil.trimLeadingSlashes(path)
      else -> path
    }
    val path = Path.of(actualPath)
    return VfsUtil.findFile(path, true)
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
