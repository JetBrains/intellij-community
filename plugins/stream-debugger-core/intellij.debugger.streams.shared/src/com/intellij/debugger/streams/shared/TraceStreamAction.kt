package com.intellij.debugger.streams.shared

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.launch

/**
 * @author Vitaliy.Bibaev
 */
open class TraceStreamAction : AnAction(), SplitDebuggerAction {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: run {
      presentation.setEnabledAndVisible(false)
      return
    }
    val chainStatus = StreamDebuggerManager.getInstance(project).getChainStatus(sessionProxy.id)
    when (chainStatus) {
      null -> presentation.setEnabledAndVisible(false)
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
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return
    sessionProxy.coroutineScope.launch {
      StreamDebuggerApi.getInstance().showTraceDebuggerDialog(sessionProxy.id)
    }
  }
}