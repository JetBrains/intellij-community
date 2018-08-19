// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager
import org.jetbrains.plugins.ipnb.debugger.IpnbDebugRunner
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel

class IpnbDebugAction(private val myFileEditor: IpnbFileEditor) : AnAction("Debug Cell", "Debug Cell", AllIcons.Actions.StartDebugger) {
  private var myDebugSession: XDebugSession? = null

  init {
    templatePresentation.text = "Debug Cell"
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val filePath = myFileEditor.virtualFile.path
    val connectionManager = IpnbConnectionManager.getInstance(project)

    if (myDebugSession == null || myDebugSession?.isStopped == true) {
      if (connectionManager.hasConnection(filePath)) {
        val selectedCellPanel = myFileEditor.ipnbFilePanel.selectedCellPanel
        if (selectedCellPanel is IpnbCodePanel) {
          myDebugSession = IpnbDebugRunner.createDebugSession(project, filePath, selectedCellPanel)
        }
      }
      else {
        // TODO: fix it!
        IpnbConnectionManager.showMessage(myFileEditor, "Connect to Kernel for debugging", null, MessageType.WARNING)
      }
    }
    else {
      IpnbConnectionManager.showMessage(myFileEditor, "Previous debug session is still running", null, MessageType.WARNING)
    }
  }
}
