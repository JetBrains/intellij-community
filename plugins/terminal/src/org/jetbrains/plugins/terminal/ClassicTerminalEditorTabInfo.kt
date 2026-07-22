// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.terminal.classic.ClassicTerminalTabCloseListener
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow

internal class ClassicTerminalEditorTabInfo(private val widget: TerminalWidget, coroutineScope: CoroutineScope) : TerminalEditorTabInfo {
  override val editorTabTitle: StateFlow<@NlsSafe String> =
    widget.terminalTitle.stateFlow(
      buildCroppedTitle = { it.buildSettingsAwareTitle() },
      buildFullTitle = { it.buildSettingsAwareFullTitle() },
    ).map { it.croppedText }
      .distinctUntilChanged()
      .stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = widget.terminalTitle.buildSettingsAwareTitle(false),
      )

  override suspend fun shouldConfirmClosing(): Boolean = ClassicTerminalTabCloseListener.shouldConfirmClosing(widget)
}
