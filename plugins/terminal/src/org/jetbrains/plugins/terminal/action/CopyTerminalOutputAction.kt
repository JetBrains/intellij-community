// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction

/**
 * @author Konstantin Bulenkov
 */
class CopyTerminalOutputAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val output = e.getData(PlatformDataKeys.EDITOR)?.document?.text
    if (output != null) {
      CopyPasteManager.copyTextToClipboard(output)
    }
  }
}
