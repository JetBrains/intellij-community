// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.shellCommandRunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TerminalRunSmartCommandAction : TerminalExecutorAction()
class TerminalDebugSmartCommandAction : TerminalExecutorAction()

abstract class TerminalExecutorAction : AnAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
  }

  override fun actionPerformed(e: AnActionEvent) {}
}