// Copyright 2000-2026 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.services

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.markdown.backend.index.HeaderAnchorIndex
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.dto.MarkdownLinkNavigationData
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper
import org.intellij.plugins.markdown.service.MarkdownLinkNavigationResolver
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi

internal class MarkdownLinkOpenerRemoteApiImpl : MarkdownLinkOpenerRemoteApi {
  override suspend fun fetchLinkNavigationData(link: String, virtualFileId: VirtualFileId?): MarkdownLinkNavigationData {
    return MarkdownLinkNavigationResolver.resolve(link, virtualFileId?.virtualFile(), ::collectHeaders)
  }

  private suspend fun collectHeaders(anchor: String, targetFile: VirtualFile, project: Project): List<MarkdownHeaderInfo> {
    return readAction {
      if (DumbService.isDumb(project)) {
        return@readAction emptyList()
      }
      val file = PsiManager.getInstance(project).findFile(targetFile) ?: return@readAction emptyList()
      return@readAction HeaderAnchorIndex.collectHeaders(file, anchor).map(MarkdownHeaderMapper::map)
    }
  }
}
