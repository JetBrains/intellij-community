// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.ui.ScrollingUtil
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal abstract class TerminalCommandUpDownScrolling(private val up: Boolean) : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    //Copy logic from LookupActionHandler#executeUpOrDown for up/down scrolling in the terminal popup
    val lookup = LookupManager.getActiveLookup(e.terminalEditor) as LookupImpl?
    if (lookup == null) {
      return
    }
    if (!lookup.isFocused) {
      val semiFocused = lookup.lookupFocusDegree == LookupFocusDegree.SEMI_FOCUSED
      lookup.setLookupFocusDegree(LookupFocusDegree.FOCUSED)
      if (!up && !semiFocused) {
        return
      }
    }
    if (up) {
      ScrollingUtil.moveUp(lookup.list, 0)
    }
    else {
      ScrollingUtil.moveDown(lookup.list, 0)
    }
    lookup.markSelectionTouched()
    lookup.refreshUi(false, true)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    if (editor == null || LookupManager.getActiveLookup(editor) == null || !editor.isOutputModelEditor) {
      e.presentation.isEnabled = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

internal class TerminalCompletionUpAction : TerminalCommandUpDownScrolling(up = true)

internal class TerminalCompletionDownAction : TerminalCommandUpDownScrolling(up = false)