// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package org.intellij.plugins.markdown.webview.preview

import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarkdownPreviewPageApi : WebViewCallable {
  fun contentChanged(params: MarkdownContentChangedParams)

  fun scrollToLine(params: MarkdownScrollToLineParams)

  fun selectionChanged(params: MarkdownSelectionChangedParams)

  companion object {
    val ID: WebViewApiId<MarkdownPreviewPageApi> = WebViewApiId.of("markdown.preview")
  }
}

@ApiStatus.Internal
interface MarkdownPreviewHostApi : WebViewImplementable {
  suspend fun pageReady()

  suspend fun openLink(params: MarkdownOpenLinkParams)

  suspend fun runCommand(params: MarkdownRunCommandParams)

  companion object {
    val ID: WebViewApiId<MarkdownPreviewHostApi> = WebViewApiId.of("markdown.preview")
  }
}

@ApiStatus.Internal
@Serializable
data class MarkdownContentChangedParams(
  val markdown: String,
  val scrollLine: Int,
  val settings: MarkdownPreviewSettingsParams,
  val commands: List<MarkdownCommandDescriptor> = emptyList(),
  val changes: List<MarkdownChangedBlockDescriptor> = emptyList(),
)

@ApiStatus.Internal
@Serializable
data class MarkdownPreviewSettingsParams(
  val fontSize: Int,
)

@ApiStatus.Internal
@Serializable
data class MarkdownScrollToLineParams(val line: Int)

@ApiStatus.Internal
@Serializable
data class MarkdownSelectionChangedParams(val selection: MarkdownPreviewSourceRange?)

@ApiStatus.Internal
@Serializable
data class MarkdownPreviewSourceRange(
  val startLine: Int,
  val startColumn: Int,
  val endLine: Int,
  val endColumn: Int,
)

@ApiStatus.Internal
@Serializable
data class MarkdownOpenLinkParams(val href: String)

@ApiStatus.Internal
@Serializable
data class MarkdownCommandDescriptor(
  val id: String,
  val kind: MarkdownPreviewCommandKind,
  val startLine: Int,
  val startColumn: Int,
  val endLine: Int,
  val endColumn: Int,
  val title: String,
  val firstLineCommandId: String? = null,
)

@ApiStatus.Internal
@Serializable
enum class MarkdownPreviewCommandKind {
  BLOCK,
  LINE,
  INLINE,
}

@ApiStatus.Internal
@Serializable
data class MarkdownChangedBlockDescriptor(
  val kind: MarkdownChangedBlockKind,
  val startLine: Int,
  val endLine: Int,
)

@ApiStatus.Internal
@Serializable
enum class MarkdownChangedBlockKind {
  ADDED,
  MODIFIED,
  REMOVED,
}

@ApiStatus.Internal
@Serializable
data class MarkdownRunCommandParams(
  val id: String,
  val clientX: Int,
  val clientY: Int,
)
