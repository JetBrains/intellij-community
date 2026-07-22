// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.frontend.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.terminal.TerminalEditorTabInfo

internal class ReworkedTerminalEditorTabInfo(private val view: TerminalView, coroutineScope: CoroutineScope) : TerminalEditorTabInfo {
  override val editorTabTitle: StateFlow<@NlsSafe String> =
    view.titleStateFlow().map { it.croppedText }
      .distinctUntilChanged()
      .stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = view.getTitleText(),
      )

  override suspend fun shouldConfirmClosing(): Boolean = TerminalTabCloseListenerImpl.shouldConfirmClosing(view)
}
