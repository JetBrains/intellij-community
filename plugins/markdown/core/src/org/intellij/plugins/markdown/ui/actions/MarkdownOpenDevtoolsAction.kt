// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.application

internal class MarkdownOpenDevtoolsAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    MarkdownActionUtil.findPreviewBrowserActions(event)?.openDevtools()
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = application.isInternal && MarkdownActionUtil.findPreviewBrowserActions(event) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
