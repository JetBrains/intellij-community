// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputModelState

@ApiStatus.Internal
object TerminalTestUtil {
  fun createOutputModel(maxLength: Int = 0): MutableTerminalOutputModelImpl {
    val document = DocumentImpl("", true)
    return MutableTerminalOutputModelImpl(document, maxLength)
  }

  suspend fun MutableTerminalOutputModel.update(absoluteLineIndex: Long, text: String, styles: List<StyleRange> = emptyList()) {
    updateOutputModel { updateContent(absoluteLineIndex, text, styles) }
  }

  suspend fun MutableTerminalOutputModel.replace(relativeStartOffset: Int, length: Int, text: String, styles: List<StyleRange> = emptyList()) {
    updateOutputModel { replaceContent(startOffset + relativeStartOffset.toLong(), length, text, styles) }
  }

  suspend fun MutableTerminalOutputModel.updateCursor(absoluteLineIndex: Long, column: Int) {
    updateOutputModel { updateCursorPosition(absoluteLineIndex, column) }
  }

  suspend fun MutableTerminalOutputModel.restore(state: TerminalOutputModelState) {
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
    val options = TerminalOptionsProvider.instance
    val prevValue = options.terminalEngine
    options.terminalEngine = engine
    Disposer.register(parentDisposable) {
      options.terminalEngine = prevValue
    }
  }
}