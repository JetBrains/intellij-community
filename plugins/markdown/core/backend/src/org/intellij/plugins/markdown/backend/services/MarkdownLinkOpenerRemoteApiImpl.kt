// Copyright 2000-2025 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.backend.services

import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.UriUtil
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

internal class MarkdownLinkOpenerRemoteApiImpl : MarkdownLinkOpenerRemoteApi {
  companion object {
    private val logger: Logger = Logger.getInstance(MarkdownLinkOpenerRemoteApiImpl::class.java)
  }

  override suspend fun openFile(projectId: ProjectId, uri: String) {
    val project = projectId.findProject()
    val parsedUri = parseUri(uri)?: return
    OpenFileAction.openFile(parsedUri.path, project)
  }

  private fun parseUri(uri: String): URI? {
    return try {
      URI(uri)
    } catch (e: URISyntaxException) {
      logger.warn(e)
      return null
    }
  }

  override suspend fun collectHeaders(projectId: ProjectId?, uri: String): List<MarkdownHeaderInfo> {
    val project = projectId?.findProject() ?: return emptyList()
    val parsedUri = parseUri(uri)?: return emptyList()
    val targetFile = parsedUri.findVirtualFile() ?: return emptyList()
    val anchor = parsedUri.fragment

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

  override suspend fun guessProjectForUri(uri: String): ProjectId? {
    val parsedUri = parseUri(uri) ?: return null
    val file = parsedUri.findVirtualFile() ?: return null
    val project = guessProjectForFile(file) ?: return null
    return project.projectId()
  }

  override suspend fun navigateToHeader(projectId: ProjectId, headerInfo: MarkdownHeaderInfo) {
    val uri = createFileUri(headerInfo.filePath) ?: return
    val file = uri.findVirtualFile() ?: return
    val project = projectId.findProject()
    val manager = FileEditorManager.getInstance(project)
    val openedEditors = manager.getEditorList(file).filterIsInstance<MarkdownEditorWithPreview>()
    val psiFile = PsiUtilCore.getPsiFile(project, file)
    val element = psiFile.findElementAt(headerInfo.textOffset) ?: return
    if (openedEditors.isNotEmpty()) {
      openedEditors.forEach {
        // Ensures the element is located as in the original implementation
        PsiUtilCore.getElementAtOffset(psiFile, element.textOffset)
        PsiNavigateUtil.navigate(element, true)
      }
      return
    }
    val descriptor = OpenFileDescriptor(project, file, element.textOffset)
    manager.openEditor(descriptor, true)
  }

  private fun URI.findVirtualFile(): VirtualFile? {
    val actualPath = when {
      SystemInfo.isWindows -> UriUtil.trimLeadingSlashes(path)
      else -> path
    }
    val path = Path.of(actualPath)
    return VfsUtil.findFile(path, true)
  }

  private fun createFileUri(link: String): URI? {
    return try {
      URI("file", null, link, null)
    } catch (e: URISyntaxException) {
      logger.warn(e)
      null
    }
  }
}