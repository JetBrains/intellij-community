// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalView

open class TerminalNewSessionAction : DumbAwareAction(
  IdeBundle.messagePointer("action.DumbAware.TerminalView.text.new.session"),
  IdeBundle.messagePointer("action.DumbAware.TerminalView.description.create.new.session"),
  AllIcons.General.Add) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val terminalView = TerminalView.getInstance(project)
    terminalView.newTab(terminalView.toolWindow, null)
  }
}