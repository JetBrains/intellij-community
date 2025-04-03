// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.dto

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MarkdownHeaderInfo(
  @NlsContexts.ListItem @SerialName("header_text")
  val headerText: String,
  @SerialName("file_path")
  val filePath: String,
  @NlsContexts.ListItem @SerialName("file_name")
  val fileName: String,
  @SerialName("line_number")
  val lineNumber: Int,
  @SerialName("text_offset")
  val textOffset: Int,
  @SerialName("file_id")
  val virtualFileId: VirtualFileId
)

@Serializable
data class MarkdownLinkNavigationData(
  @SerialName("uri")
  val uri: String,
  @SerialName("file")
  val virtualFileId: VirtualFileId?,
  @SerialName("project")
  val projectId: ProjectId?,
  @SerialName("headers")
  val headers: List<MarkdownHeaderInfo>?
)