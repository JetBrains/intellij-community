// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.ipnb.debugger.IpnbDebugRunner
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor

class IpnbDebugAction(private val myFileEditor: IpnbFileEditor) : AnAction("Debug Cell", "Debug Cell", AllIcons.Actions.StartDebugger) {

  init {
    templatePresentation.text = "Debug Cell"
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    IpnbDebugRunner.createDebugSession(project)
  }
}
