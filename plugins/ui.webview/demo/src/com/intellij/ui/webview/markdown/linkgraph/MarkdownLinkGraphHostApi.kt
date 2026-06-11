// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.markdown.linkgraph

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewImplementable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface MarkdownLinkGraphHostApi : WebViewImplementable {
  suspend fun getGraph(): MarkdownGraphDto

  suspend fun openFile(params: MarkdownOpenFileRequest): MarkdownOpenFileResult

  suspend fun getFilePreview(params: MarkdownFilePreviewRequest): MarkdownFilePreviewDto

  companion object {
    val ID: WebViewApiId<MarkdownLinkGraphHostApi> = WebViewApiId.of("markdown.linkGraph")
  }
}

internal class MarkdownLinkGraphHostApiImpl(private val project: Project) : MarkdownLinkGraphHostApi {
  override suspend fun getGraph(): MarkdownGraphDto {
    return readAction { MarkdownLinkGraphService(project).buildGraph() }
  }

  override suspend fun openFile(params: MarkdownOpenFileRequest): MarkdownOpenFileResult {
    val file = readAction { MarkdownLinkGraphService(project).findFileById(params.fileId) }
                ?: return MarkdownOpenFileResult(false)
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
    return MarkdownOpenFileResult(true)
  }

  override suspend fun getFilePreview(params: MarkdownFilePreviewRequest): MarkdownFilePreviewDto {
    return readAction {
      val file = MarkdownLinkGraphService(project).findFileById(params.fileId)
                 ?: return@readAction MarkdownFilePreviewDto(
                   found = false,
                   fileId = params.fileId,
                   title = "File not found",
                   path = params.fileId.removePrefix(FILE_ID_PREFIX),
                   text = "",
                   truncated = false,
                 )
      val text = VfsUtilCore.loadText(file)
      val truncated = text.length > MAX_PREVIEW_TEXT_LENGTH
      MarkdownFilePreviewDto(
        found = true,
        fileId = params.fileId,
        title = file.name,
        path = params.fileId.removePrefix(FILE_ID_PREFIX),
        text = if (truncated) text.take(MAX_PREVIEW_TEXT_LENGTH) else text,
        truncated = truncated,
      )
    }
  }

  companion object {
    private const val FILE_ID_PREFIX = "file:"
    private const val MAX_PREVIEW_TEXT_LENGTH = 200_000
  }
}
