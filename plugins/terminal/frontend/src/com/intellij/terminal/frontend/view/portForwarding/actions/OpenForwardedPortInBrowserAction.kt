// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.terminal.frontend.view.portForwarding.PortForwardingItem
import org.jetbrains.plugins.terminal.TerminalBundle

internal class OpenForwardedPortInBrowserAction :
  DumbAwareAction(TerminalBundle.messagePointer("action.Terminal.PortForwarding.OpenInBrowser.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val item = e.getData(PortForwardingItem.KEY) as? PortForwardingItem.Forwarded ?: return
    BrowserUtil.browse("http://127.0.0.1:${item.localPort}")
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(PortForwardingItem.KEY) is PortForwardingItem.Forwarded
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}