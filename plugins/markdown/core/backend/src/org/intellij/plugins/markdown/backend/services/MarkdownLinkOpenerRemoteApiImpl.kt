// Copyright 2000-2025 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.backend.services

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.UriUtil
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

internal class MarkdownLinkOpenerRemoteApiImpl : MarkdownLinkOpenerRemoteApi {
  companion object {
    private val logger: Logger = Logger.getInstance(MarkdownLinkOpenerRemoteApiImpl::class.java)
  }

  /**
   * Tries to resolve the link as a path to file. Path to file can be:
   * - absolute path
   * - relative path to the file from which the link is resolved
   * @param link the link to resolve
   * @param virtualFileId the id of the file from which the link is resolved
   * @return the path to the file as schema if the link is resolved successfully, null otherwise
   */
  override suspend fun resolveLinkAsFilePath(link: String, virtualFileId: VirtualFileId?): String?{
    val containingFile = virtualFileId?.virtualFile()?.parent ?: return null
    val targetFile = containingFile.findFile(link.trimAnchor()) ?: return null
    val anchor = if ('#' in link) link.substring(link.lastIndexOf('#')) else ""
    return targetFile.url + anchor
  }

  private fun String.trimAnchor(): String {
    val anchorIndex = lastIndexOf('#')
    return if (anchorIndex == -1) this else substring(0, anchorIndex)
  }

  private fun parseUri(uri: String): URI? {
    return try {
      URI(uri)
    } catch (e: URISyntaxException) {
      logger.warn(e)
      return null
    }
  }

  override suspend fun collectHeaders(projectId: ProjectId?, uri: String): List<MarkdownHeaderInfo>? {
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

  private fun URI.findVirtualFile(): VirtualFile? {
    val actualPath = when {
      SystemInfo.isWindows -> UriUtil.trimLeadingSlashes(path)
      else -> path
    }
    val path = Path.of(actualPath)
    return VfsUtil.findFile(path, true)
  }
}