// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.project.projectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun confirmBackendProjectIsTrusted(project: Project, api: MarkdownCommandRunnerRemoteApi): Boolean {
  val projectId = project.projectId()
  if (api.isProjectTrusted(projectId)) {
    return true
  }
  val trusted = withContext(Dispatchers.EDT) {
    MessageDialogBuilder
      .yesNo(
        IdeBundle.message("untrusted.project.general.dialog.title"),
        MarkdownBundle.message("markdown.untrusted.project.dialog.text")
      )
      .yesText(IdeBundle.message("untrusted.project.dialog.trust.button"))
      .noText(IdeBundle.message("untrusted.project.dialog.distrust.button"))
      .asWarning()
      .ask(project)
  }
  if (!trusted) {
    return false
  }
  api.setProjectTrusted(projectId)
  return true
}
