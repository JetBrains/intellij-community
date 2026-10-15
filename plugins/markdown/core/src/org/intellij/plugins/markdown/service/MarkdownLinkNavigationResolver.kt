// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.util.Urls
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.dto.MarkdownLinkNavigationData
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewPathResolver
import org.jetbrains.annotations.ApiStatus

/**
 * Turns a link of a Markdown preview into [MarkdownLinkNavigationData].
 *
 * The backend remote API and a light process without a backend share this code.
 * Only the header lookup differs: the backend reads its index, and a light process reads the PSI.
 */
@ApiStatus.Internal
object MarkdownLinkNavigationResolver {
  /**
   * Resolves [link] against [document] through [MarkdownPreviewPathResolver].
   *
   * [collectHeaders] gets the anchor of the link, the target file and its project.
   * It returns `null` when it cannot answer, and the caller shows the dumb mode notification.
   */
  suspend fun resolve(
    link: String,
    document: VirtualFile?,
    collectHeaders: suspend (anchor: String, file: VirtualFile, project: Project) -> List<MarkdownHeaderInfo>?,
  ): MarkdownLinkNavigationData {
    val file = resolveLinkAsFile(link, document)
               ?: return MarkdownLinkNavigationData(link, null, null, null)

    var path = Urls.toUriWithoutParameters(Urls.newFromVirtualFile(file)).toString()
    val project = guessProjectForFile(file)
                  ?: return MarkdownLinkNavigationData(path, file.rpcId(), null, null)

    val anchor = extractAnchor(link)
    if (anchor.isEmpty()) {
      return MarkdownLinkNavigationData(path, file.rpcId(), project.projectId(), null)
    }

    path += "#$anchor"
    return MarkdownLinkNavigationData(path, file.rpcId(), project.projectId(), collectHeaders(anchor, file, project))
  }

  /** Collects the headers of [anchor] from the PSI of [file]. A process without an index uses this lookup. */
  suspend fun collectHeadersFromPsi(anchor: String, file: VirtualFile, project: Project): List<MarkdownHeaderInfo> {
    return readAction {
      val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@readAction emptyList()
      MarkdownHeader.findByAnchor(psiFile, anchor).map(MarkdownHeaderMapper::map)
    }
  }

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

  /**
   * Defers to the resolver the preview already uses for an image, so a link reaches a file through the
   * same rules: percent escapes, a query string, a Windows drive letter, and a path that names the
   * project rather than the document.
   */
  private suspend fun resolveLinkAsFile(link: String, document: VirtualFile?): VirtualFile? {
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
}
