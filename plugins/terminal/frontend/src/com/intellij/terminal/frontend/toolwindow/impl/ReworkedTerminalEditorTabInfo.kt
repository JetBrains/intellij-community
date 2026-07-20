// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.frontend.view.TerminalView
import org.jetbrains.plugins.terminal.TerminalEditorTabInfo

internal class ReworkedTerminalEditorTabInfo(private val view: TerminalView) : TerminalEditorTabInfo {
  @NlsSafe
  override fun getEditorTabTitle(): String = view.getTitleText()

  override suspend fun shouldConfirmClosing(): Boolean = TerminalTabCloseListenerImpl.shouldConfirmClosing(view)
}
