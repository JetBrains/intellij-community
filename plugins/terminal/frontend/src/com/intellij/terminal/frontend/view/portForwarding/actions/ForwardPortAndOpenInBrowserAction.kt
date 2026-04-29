// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.terminal.frontend.view.portForwarding.PortForwardingItem
import com.intellij.terminal.frontend.view.portForwarding.PortForwardingViewModel
import com.intellij.terminal.frontend.view.portForwarding.TerminalPortForwardingManager
import com.intellij.terminal.frontend.view.portForwarding.TerminalPortForwardingPersistenceService
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalBundle

internal class ForwardPortAndOpenInBrowserAction :
  DumbAwareAction(TerminalBundle.messagePointer("action.Terminal.PortForwarding.ForwardPortAndOpenInBrowser.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val item = e.getData(PortForwardingItem.KEY) as? PortForwardingItem.NotForwarded ?: return
    val model = e.getData(PortForwardingViewModel.KEY) ?: return
    e.coroutineScope.launch {
      val localPort = TerminalPortForwardingManager.getInstance().forwardPort(model.eelDescriptor, item.remotePort)
                      ?: return@launch
      TerminalPortForwardingPersistenceService.getInstance(project).persistPort(item.remotePort)
      BrowserUtil.browse("http://127.0.0.1:$localPort")
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
                                         && e.getData(PortForwardingItem.KEY) is PortForwardingItem.NotForwarded
                                         && e.getData(PortForwardingViewModel.KEY) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}