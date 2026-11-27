// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package org.intellij.plugins.markdown.service

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.project.projectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.TrustedProjectUtil
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import org.jetbrains.annotations.ApiStatus

fun runMarkdownCommandIfTrusted(project: Project, action: () -> Boolean): Boolean {
  if (project.isDisposed) return false
  val api = MarkdownCommandRunnerRemoteApi.tryGetInstance()
  if (api == null) {
    if (LiteRemoteApiProviderService.isConnected()) return false
    var accepted = false
    val trusted = TrustedProjectUtil.executeIfTrusted(project) { accepted = action() }
    return trusted && accepted
  }
  MarkdownPluginScope.scope(project).launch {
    try {
      if (!confirmBackendProjectIsTrusted(project, api)) return@launch
      withContext(Dispatchers.EDT) {
        if (!project.isDisposed && !action()) {
          LOG.info("The Markdown runner declined the command.")
        }
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to run a Markdown code fence.", e)
    }
  }
  return true
}

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

private val LOG = fileLogger()
