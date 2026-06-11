// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.markdown.linkgraph

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class MarkdownGraphDto(
  val nodes: List<MarkdownGraphNodeDto>,
  val edges: List<MarkdownGraphEdgeDto>,
  val truncated: Boolean,
)

@ApiStatus.Internal
@Serializable
data class MarkdownGraphNodeDto(
  val id: String,
  val label: String,
  val kind: String,
  val path: String? = null,
  val parent: String? = null,
)

@ApiStatus.Internal
@Serializable
data class MarkdownGraphEdgeDto(
  val id: String,
  val source: String,
  val target: String,
)

@ApiStatus.Internal
@Serializable
data class MarkdownOpenFileRequest(
  val fileId: String,
)

@ApiStatus.Internal
@Serializable
data class MarkdownOpenFileResult(
  val opened: Boolean,
)

@ApiStatus.Internal
@Serializable
data class MarkdownFilePreviewRequest(
  val fileId: String,
)

@ApiStatus.Internal
@Serializable
data class MarkdownFilePreviewDto(
  val found: Boolean,
  val fileId: String,
  val title: String,
  val path: String,
  val text: String,
  val truncated: Boolean,
)
