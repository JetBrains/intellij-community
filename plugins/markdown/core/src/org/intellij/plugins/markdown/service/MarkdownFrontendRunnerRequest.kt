// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCommandWorkingDirectoryPaths
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class MarkdownFrontendRunnerRequest(
  val projectId: ProjectId,
  val languageId: String?,
  val command: String,
  val sourceFileUrl: String,
  val showTargetChooser: Boolean,
  val offset: Int,
  val workingDirectoryPaths: MarkdownCommandWorkingDirectoryPaths,
  val screenPoint: ScreenPoint? = null,
  val editorId: EditorId,
) {
  @Serializable
  data class ScreenPoint(val x: Int, val y: Int)
}
