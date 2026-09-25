// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.session.impl.TerminalSetDefaultCursorShapeEvent
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import java.beans.PropertyChangeListener

/**
 * Sends the terminal "Cursor shape" setting ([TerminalOptionsProvider]) and the editor "Blink caret" setting
 * ([EditorSettingsExternalizable]) to the session as its default cursor shape, and sends it again when a setting changes it.
 */
internal fun installSessionCursorStateUpdating(terminalInput: TerminalInput, coroutineScope: CoroutineScope) {
  val disposable = coroutineScope.asDisposable()
  var lastCursorShape: CursorShapeDto? = null

  fun sendCursorShapeIfChanged() {
    val isBlinking = EditorSettingsExternalizable.getInstance().isBlinkCaret
    val cursorShape = TerminalOptionsProvider.instance.cursorShape.toDto(isBlinking)
    if (cursorShape != lastCursorShape) {
      lastCursorShape = cursorShape
      terminalInput.sendEvent(TerminalSetDefaultCursorShapeEvent(cursorShape))
    }
  }

  TerminalOptionsProvider.instance.addListener(disposable) { sendCursorShapeIfChanged() }
  EditorSettingsExternalizable.getInstance().addPropertyChangeListener(PropertyChangeListener { event ->
    if (event.propertyName == EditorSettingsExternalizable.PropNames.PROP_IS_CARET_BLINKING) {
      sendCursorShapeIfChanged()
    }
  }, disposable)

  sendCursorShapeIfChanged()
}

private fun TerminalUiSettingsManager.CursorShape.toDto(isBlinking: Boolean): CursorShapeDto = when (this) {
  TerminalUiSettingsManager.CursorShape.BLOCK -> if (isBlinking) CursorShapeDto.BLINK_BLOCK else CursorShapeDto.STEADY_BLOCK
  TerminalUiSettingsManager.CursorShape.UNDERLINE -> if (isBlinking) CursorShapeDto.BLINK_UNDERLINE else CursorShapeDto.STEADY_UNDERLINE
  TerminalUiSettingsManager.CursorShape.VERTICAL -> if (isBlinking) CursorShapeDto.BLINK_VERTICAL_BAR else CursorShapeDto.STEADY_VERTICAL_BAR
}
