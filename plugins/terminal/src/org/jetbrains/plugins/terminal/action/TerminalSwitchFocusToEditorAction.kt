// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class TerminalSwitchFocusToEditorAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    // This method is never called (the action is disabled) to avoid being triggered in wrong contexts.
    // Action is performed in com.intellij.terminal.TerminalEscapeKeyListener
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
  }
}
