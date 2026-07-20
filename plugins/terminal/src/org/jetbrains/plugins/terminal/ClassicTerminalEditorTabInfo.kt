// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.classic.ClassicTerminalTabCloseListener
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle

internal class ClassicTerminalEditorTabInfo(private val widget: TerminalWidget) : TerminalEditorTabInfo {
  @NlsSafe
  override fun getEditorTabTitle(): String = widget.terminalTitle.buildSettingsAwareTitle(false)

  override suspend fun shouldConfirmClosing(): Boolean = ClassicTerminalTabCloseListener.shouldConfirmClosing(widget)
}
