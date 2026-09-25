// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.session.impl.TerminalSetColorSchemeEvent
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalColorSchemeDto
import org.jetbrains.plugins.terminal.session.impl.dto.toRgbColorDto

/**
 * Sends the terminal colors of the global color scheme ([TerminalColorSchemeDto]) to the session,
 * and sends them again when the global color scheme ([EditorColorsManager.TOPIC]) changes them.
 */
internal fun installSessionColorSchemeUpdating(terminalInput: TerminalInput, coroutineScope: CoroutineScope) {
  var lastColorScheme: TerminalColorSchemeDto? = null

  fun sendColorSchemeIfChanged() {
    val colorScheme = TerminalColorSchemeDto(
      foreground = TerminalUi.defaultForeground().toRgbColorDto(),
      background = TerminalUi.defaultBackground().toRgbColorDto(),
    )
    if (colorScheme != lastColorScheme) {
      lastColorScheme = colorScheme
      terminalInput.sendEvent(TerminalSetColorSchemeEvent(colorScheme))
    }
  }

  ApplicationManager.getApplication().messageBus
    .connect(coroutineScope.asDisposable())
    .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { sendColorSchemeIfChanged() })

  sendColorSchemeIfChanged()
}
