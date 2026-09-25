// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.session.impl.TerminalSetDefaultBackgroundEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSetDefaultForegroundEvent
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalRgbColorDto
import org.jetbrains.plugins.terminal.session.impl.dto.toRgbColorDto

/**
 * Sends the IDE terminal colors ([TerminalUi.defaultForeground] and [TerminalUi.defaultBackground]) to the session
 * as its default colors, and sends them again when the global color scheme ([EditorColorsManager.TOPIC]) changes them.
 */
internal fun installSessionColorSchemeUpdating(terminalInput: TerminalInput, coroutineScope: CoroutineScope) {
  var lastForeground: TerminalRgbColorDto? = null
  var lastBackground: TerminalRgbColorDto? = null

  fun sendColorsIfChanged() {
    val foreground = TerminalUi.defaultForeground().toRgbColorDto()
    if (foreground != lastForeground) {
      lastForeground = foreground
      terminalInput.sendEvent(TerminalSetDefaultForegroundEvent(foreground))
    }
    val background = TerminalUi.defaultBackground().toRgbColorDto()
    if (background != lastBackground) {
      lastBackground = background
      terminalInput.sendEvent(TerminalSetDefaultBackgroundEvent(background))
    }
  }

  ApplicationManager.getApplication().messageBus
    .connect(coroutineScope.asDisposable())
    .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { sendColorsIfChanged() })

  sendColorsIfChanged()
}
