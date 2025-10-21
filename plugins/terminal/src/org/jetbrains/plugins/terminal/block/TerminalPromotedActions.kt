// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class TerminalPromotedDumbAwareAction : DumbAwareAction(), ActionPromoter, ActionRemoteBehaviorSpecification.Frontend {
  /**
   * Prioritize terminal actions if there are actions with the same shortcuts.
   * It's safe because terminal actions are enabled only in the terminal.
   */
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }
}

@ApiStatus.Internal
abstract class TerminalPromotedEditorAction(handler: EditorActionHandler) : EditorAction(handler), ActionPromoter {
  /**
   * Prioritize terminal actions if there are actions with the same shortcuts.
   * It's safe because terminal actions are enabled only in the terminal.
   */
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }
}
