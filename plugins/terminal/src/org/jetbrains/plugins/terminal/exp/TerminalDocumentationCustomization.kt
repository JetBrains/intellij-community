// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.ActionsBundle
import com.intellij.lang.documentation.ide.DocumentationCustomization
import com.intellij.lang.documentation.ide.actions.AdjustFontSizeAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.terminal.TerminalUiSettingsManager
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor

class TerminalDocumentationCustomization : DocumentationCustomization {
  override val isAutoShowOnLookupItemChange: Boolean
    get() = TerminalUiSettingsManager.getInstance().autoShowDocumentationPopup
  override val autoShowDelayMillis: Long = 0
  override val isShowToolbar: Boolean = false

  override fun editGearActions(group: DefaultActionGroup): ActionGroup {
    return DefaultActionGroup().apply {
      isPopup = true
      add(TerminalToggleAutoShowDocumentationAction())
      add(AdjustFontSizeAction())
    }
  }

  override fun isAvailable(editor: Editor): Boolean {
    return editor.isPromptEditor
  }
}

private class TerminalToggleAutoShowDocumentationAction : ToggleAction(
  ActionsBundle.actionText("Documentation.ToggleAutoShow"),
  ActionsBundle.actionDescription("Documentation.ToggleAutoShow"),
  null
), HintManagerImpl.ActionToIgnore {
  override fun isSelected(e: AnActionEvent): Boolean {
    return TerminalUiSettingsManager.getInstance().autoShowDocumentationPopup
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    TerminalUiSettingsManager.getInstance().autoShowDocumentationPopup = state
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val visible = project != null && LookupManager.getInstance(project).activeLookup != null
    e.presentation.isEnabledAndVisible = visible
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}