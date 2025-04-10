// Copyright 2000-2025 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.services

import com.intellij.ide.BrowserUtil
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.UriUtil
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.dto.MarkdownLinkNavigationData
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

internal class MarkdownLinkOpenerRemoteApiImpl : MarkdownLinkOpenerRemoteApi {
  companion object {
    private val logger: Logger = Logger.getInstance(MarkdownLinkOpenerRemoteApiImpl::class.java)

    private fun extractAnchor(link: String): String {
      val lastHashIndex = link.lastIndexOf('#')
      if (lastHashIndex == -1) {
        return ""
      }
      val potentialAnchor = link.substring(lastHashIndex + 1)
      if (potentialAnchor.contains("/") || potentialAnchor.contains("\\")) {
        return ""
      }
      return potentialAnchor
    }

    private fun String.trimAnchor(): String {
      val anchorIndex = lastIndexOf('#')
      return if (anchorIndex == -1) this else substring(0, anchorIndex)
    }

    private fun URI.findVirtualFile(): VirtualFile? {
      val actualPath = when {
        SystemInfo.isWindows -> UriUtil.trimLeadingSlashes(path)
        else -> path
      }
      val path = Path.of(actualPath)
      return VfsUtil.findFile(path, true)
    }

    private fun createUri(link: String): URI? {
      return try {
        URI(link)
      } catch (exception: URISyntaxException) {
        logger.warn(exception)
        null
      }
    }
  }

  override suspend fun fetchLinkNavigationData(link: String, virtualFileId: VirtualFileId?): MarkdownLinkNavigationData {
    val file = resolveLinkAsFile(link, virtualFileId)?: return MarkdownLinkNavigationData(link, null, null, null)
    var path = file.url
    val anchor = extractAnchor(link)
    if (!anchor.isEmpty()) path += "#$anchor"
    val project = guessProjectForFile(file)?: return MarkdownLinkNavigationData(path, file.rpcId(), null, null)
    if (anchor.isEmpty()) return MarkdownLinkNavigationData(path, file.rpcId(), project.projectId(), null)
    val headers = collectHeaders(anchor, file, project)
    return MarkdownLinkNavigationData(path, file.rpcId(), project.projectId(), headers)
  }

  private fun resolveLinkAsFile(link: String, virtualFileId: VirtualFileId?): VirtualFile?{
    if (BrowserUtil.isAbsoluteURL(link)){
      val uri = createUri(link)
      if (uri != null && uri.scheme == "file") {
        return uri.findVirtualFile()
      }
    }
    val containingFile = virtualFileId?.virtualFile()?.parent ?: return null
    val targetFile = containingFile.findFile(link.trimAnchor()) ?: return null
    return targetFile
  }

  private fun collectHeaders(anchor: String, targetFile: VirtualFile, project: Project): List<MarkdownHeaderInfo>? {
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
}