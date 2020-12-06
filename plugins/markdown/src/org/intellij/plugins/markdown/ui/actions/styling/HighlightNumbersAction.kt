// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.util.FileContentUtilCore

class HighlightNumbersAction : ToggleAction() {

  override fun isSelected(e: AnActionEvent): Boolean = isHighlightingEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PropertiesComponent.getInstance().setValue(highlightingEnabledKey, state, true)

    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    FileContentUtilCore.reparseFiles(virtualFile)
  }

  companion object {
    val isHighlightingEnabled: Boolean
      get() = PropertiesComponent.getInstance().getBoolean(highlightingEnabledKey, true)

    private const val highlightingEnabledKey = "markdown.highlight.numbers.enabled"
  }
}