// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.application
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel

internal class MarkdownOpenDevtoolsAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val panel = MarkdownActionUtil.findMarkdownPreviewEditor(event)?.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get()
    (panel as? JCEFHtmlPanel)?.openDevtools()
  }

  override fun update(event: AnActionEvent) {
    val panel = MarkdownActionUtil.findMarkdownPreviewEditor(event)?.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get()
    event.presentation.isEnabledAndVisible = application.isInternal && panel is MarkdownJCEFHtmlPanel
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
