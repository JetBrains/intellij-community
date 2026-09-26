// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification

/**
 * A marker action mimicking the platform's own [com.intellij.openapi.editor.actions.CreateRectangularSelectionOnMouseDragAction],
 * but with an additional `Shift+Alt+Click` shortcut that only takes effect in terminal editors,
 * via [com.intellij.terminal.frontend.view.impl.TerminalMouseActionsOverrider].
 */
internal class TerminalCreateRectangularSelectionOnMouseDragAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    // actual logic is implemented in EditorImpl
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
  }
}