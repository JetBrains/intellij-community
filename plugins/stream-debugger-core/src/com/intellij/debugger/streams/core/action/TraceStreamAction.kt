// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

/**
 * @author Vitaliy.Bibaev
 */
open class TraceStreamAction : AnAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val chainStatus = TraceStreamRunner.getInstance(project).getChainStatus(DebuggerUIUtil.getSession(e))
    val presentation = e.presentation
    when (chainStatus) {
      ChainStatus.LANGUAGE_NOT_SUPPORTED -> presentation.setEnabledAndVisible(false)
      ChainStatus.COMPUTING -> {
        presentation.setVisible(true)
        presentation.setEnabled(false)
      }
      ChainStatus.FOUND -> presentation.setEnabledAndVisible(true)
      ChainStatus.NOT_FOUND -> {
        presentation.setVisible(true)
        presentation.setEnabled(false)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    TraceStreamRunner.getInstance(project).actionPerformed(DebuggerUIUtil.getSession(e))
  }
}
