// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.*
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class MarkdownActionPromoter: ActionPromoter {
  companion object {
    private val promotedActions = setOf(
      "org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction",
      "org.intellij.plugins.markdown.ui.actions.styling.ToggleItalicAction",
      "org.intellij.plugins.markdown.ui.actions.styling.ToggleStrikethroughAction",
      "org.intellij.plugins.markdown.ui.actions.styling.ToggleCodeSpanAction",
      "Markdown.Styling.CreateLink",
      "Markdown.Insert"
    )
  }

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    if (context.getData(CommonDataKeys.PSI_FILE) !is MarkdownFile) return emptyList()

    return actions.filter { ActionManager.getInstance().getId(it) in promotedActions }
  }
}
