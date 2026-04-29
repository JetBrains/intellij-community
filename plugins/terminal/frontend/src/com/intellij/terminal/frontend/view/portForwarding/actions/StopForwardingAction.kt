// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.eel.provider.resolveEelMachine
import com.intellij.terminal.frontend.view.portForwarding.PortForwardingItem
import com.intellij.terminal.frontend.view.portForwarding.PortForwardingViewModel
import com.intellij.terminal.frontend.view.portForwarding.TerminalPortForwardingManager
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalBundle

internal class StopForwardingAction : DumbAwareAction(TerminalBundle.messagePointer("action.Terminal.PortForwarding.Stop.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val item = e.getData(PortForwardingItem.KEY) as? PortForwardingItem.Forwarded ?: return
    val model = e.getData(PortForwardingViewModel.KEY) ?: return
    e.coroutineScope.launch {
      val eelMachine = model.eelDescriptor.resolveEelMachine()
      TerminalPortForwardingManager.getInstance().stopForwarding(eelMachine, item.remotePort)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(PortForwardingItem.KEY) is PortForwardingItem.Forwarded
                                         && e.getData(PortForwardingViewModel.KEY) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}