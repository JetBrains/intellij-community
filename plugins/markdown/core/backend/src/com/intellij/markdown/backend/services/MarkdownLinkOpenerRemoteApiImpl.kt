// Copyright 2000-2025 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.services

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.markdown.backend.index.HeaderAnchorIndex
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.util.Urls
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.dto.MarkdownLinkNavigationData
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewPathResolver

internal class MarkdownLinkOpenerRemoteApiImpl : MarkdownLinkOpenerRemoteApi {
  companion object {
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
  }

  override suspend fun fetchLinkNavigationData(link: String, virtualFileId: VirtualFileId?): MarkdownLinkNavigationData {
    val file = resolveLinkAsFile(link, virtualFileId)
               ?: return MarkdownLinkNavigationData(link, null, null, null)

    var path = Urls.toUriWithoutParameters(Urls.newFromVirtualFile(file)).toString()
    val project = guessProjectForFile(file)
                  ?: return MarkdownLinkNavigationData(path, file.rpcId(), null, null)

    val anchor = extractAnchor(link)
    if (anchor.isEmpty()) {
      return MarkdownLinkNavigationData(path, file.rpcId(), project.projectId(), null)
    }

    path += "#$anchor"
    val headers = collectHeaders(anchor, file, project)
    return MarkdownLinkNavigationData(path, file.rpcId(), project.projectId(), headers)
  }

  /**
   * Defers to the resolver the preview already uses for an image, so a link reaches a file through the
   * same rules: percent escapes, a query string, a Windows drive letter, and a path that names the
   * project rather than the document.
   */
  private suspend fun resolveLinkAsFile(link: String, virtualFileId: VirtualFileId?): VirtualFile? {
    val document = virtualFileId?.virtualFile()
    val projectRoot = document
      ?.let { guessProjectForFile(it) }
      ?.let { BaseProjectDirectories.getInstance(it).getBaseDirectoryFor(document) }
    // A reader follows a link on purpose, unlike an image the preview loads on its own, so a link is
    // not held to the root of an untrusted project.
    val resolution = MarkdownPreviewPathResolver.resolve(
      document = document,
      projectRoot = projectRoot,
      rawSource = link,
      allowOutsideProjectRoot = true,
    )
    return (resolution as? MarkdownPreviewPathResolver.Resolution.Found)?.file
  }

  private fun collectHeaders(anchor: String, targetFile: VirtualFile, project: Project): List<MarkdownHeaderInfo> {
    return runReadAction {
      if (DumbService.isDumb(project)) {
        return@runReadAction emptyList()
      }
      val file = PsiManager.getInstance(project).findFile(targetFile) ?: return@runReadAction emptyList()
      return@runReadAction HeaderAnchorIndex.collectHeaders(file, anchor).map(MarkdownHeaderMapper::map)
    }
  }
}
