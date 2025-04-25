// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.session.TerminalOutputModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl

@ApiStatus.Internal
object TerminalTestUtil {
  fun createOutputModel(maxLength: Int = 0): TerminalOutputModelImpl {
    val document = DocumentImpl("", true)
    return TerminalOutputModelImpl(document, maxLength)
  }

  suspend fun TerminalOutputModel.update(absoluteLineIndex: Long, text: String, styles: List<StyleRange> = emptyList()) {
    updateOutputModel { updateContent(absoluteLineIndex, text, styles) }
  }

  suspend fun TerminalOutputModel.updateCursor(absoluteLineIndex: Long, column: Int) {
    updateOutputModel { updateCursorPosition(absoluteLineIndex, column) }
  }

  suspend fun TerminalOutputModel.restore(state: TerminalOutputModelState) {
    updateOutputModel { restoreFromState(state) }
  }

  private suspend fun updateOutputModel(action: () -> Unit) {
    withContext(Dispatchers.EDT) {
      action()
    }
  }

  /**
   * Updates the terminal engine value to [engine], but returns the previous value on [parentDisposable] dispose.
   */
  fun setTerminalEngineForTest(engine: TerminalEngine, parentDisposable: Disposable) {
    TerminalOptionsProvider.instance.setTerminalEngineForTest(engine, parentDisposable)
  }
}