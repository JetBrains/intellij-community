// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class InsertAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val dataContext = event.dataContext
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      MarkdownBundle.message("action.Markdown.Insert.text"),
      insertGroup,
      dataContext,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      MarkdownActionPlaces.INSERT_POPUP
    )
    popup.showInBestPositionFor(dataContext)
  }

  override fun update(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownTextEditor(event)
    event.presentation.isEnabledAndVisible = editor != null || event.getData(PlatformDataKeys.PSI_FILE) is MarkdownFile
  }

  companion object {
    private val insertGroup
      get() = requireNotNull(ActionUtil.getActionGroup("Markdown.InsertGroup"))
  }
}
