// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorMouseActionsOverrider
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

/**
 * Overrides [com.intellij.openapi.editor.actions.CreateRectangularSelectionOnMouseDragAction]
 * with [com.intellij.terminal.frontend.action.TerminalCreateRectangularSelectionOnMouseDragAction]
 * in Reworked Terminal-own editors.
 */
internal class TerminalMouseActionsOverrider : EditorMouseActionsOverrider {
  override fun getCreateRectangularSelectionOnMouseDragActionId(editor: Editor): String? {
    return if (editor.isReworkedTerminalEditor) {
      "Terminal.CreateRectangularSelectionOnMouseDrag"
    }
    else null
  }
}