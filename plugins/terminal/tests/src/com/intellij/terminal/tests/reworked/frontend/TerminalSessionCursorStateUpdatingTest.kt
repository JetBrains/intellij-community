// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.tests.reworked.util.BEL
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.jediterm.terminal.CursorShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.session.impl.TerminalState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * [com.intellij.terminal.frontend.view.impl.TerminalViewImpl] sends the terminal "Cursor shape" setting ([TerminalOptionsProvider])
 * and the editor "Blink caret" setting ([EditorSettingsExternalizable]) to the session as its default cursor shape,
 * and sends it again when a setting changes it.
 * The session reports the effective cursor shape in [TerminalState.cursorShape].
 *
 * Ghostty-only: JediTerm reports no cursor shape until a program sets one.
 */
internal class TerminalSessionCursorStateUpdatingTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {
  private var stateMarkerCount = 0

  @BeforeEach
  fun assumeGhosttyEmulator() {
    assumeGhostty()
  }

  @Test
  fun `the initial state reports the cursor settings`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.VERTICAL)
    setBlinkCaretForTest(true)

    doTest { fixture ->
      fixture.awaitCursorShape(CursorShape.BLINK_VERTICAL_BAR)
    }
  }

  @Test
  fun `a cursor shape setting change mid-session updates the default`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.BLOCK)
    setBlinkCaretForTest(false)

    doTest { fixture ->
      fixture.awaitCursorShape(CursorShape.STEADY_BLOCK)

      setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.VERTICAL)

      fixture.awaitCursorShape(CursorShape.STEADY_VERTICAL_BAR)
    }
  }

  @Test
  fun `a blink caret setting change mid-session updates the default independently of the shape`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.UNDERLINE)
    setBlinkCaretForTest(false)

    doTest { fixture ->
      fixture.awaitCursorShape(CursorShape.STEADY_UNDERLINE)

      setBlinkCaretForTest(true)

      fixture.awaitCursorShape(CursorShape.BLINK_UNDERLINE)
    }
  }

  @Test
  fun `a program cursor shape override survives a cursor setting change`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.UNDERLINE)
    setBlinkCaretForTest(false)

    doTest { fixture ->
      fixture.awaitCursorShape(CursorShape.STEADY_UNDERLINE)
      fixture.connector.feed(csi("2 q")) // DECSCUSR: steady block
      fixture.awaitCursorShape(CursorShape.STEADY_BLOCK)

      setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.VERTICAL)
      setBlinkCaretForTest(true)

      assertThat(fixture.awaitStateAfterInputEvents().cursorShape)
        .describedAs("a cursor setting change must not replace an active DECSCUSR override")
        .isEqualTo(CursorShape.STEADY_BLOCK)

      // DECSCUSR 0 resets the override: the cursor falls back to the new default.
      fixture.connector.feed(csi("0 q"))
      fixture.awaitCursorShape(CursorShape.BLINK_VERTICAL_BAR)
    }
  }

  private suspend fun TerminalViewFixture.awaitCursorShape(expected: CursorShape) {
    awaitState { it.cursorShape == expected }
  }

  /**
   * Returns a session state that the session projects after it handles every input event sent before this call.
   * The session changes the title only after the input barrier, so a state with the new title comes after the input events.
   */
  private suspend fun TerminalViewFixture.awaitStateAfterInputEvents(): TerminalState {
    awaitInputEventsHandled()
    val title = "state marker #${++stateMarkerCount}"
    connector.feed("$ESC]2;$title$BEL")
    return awaitState { it.windowTitle == title }
  }

  private suspend fun TerminalViewFixture.awaitState(condition: (TerminalState) -> Boolean): TerminalState {
    val stateFlow = view.sessionModel.terminalState
    val state = withTimeoutOrNull(5.seconds) { stateFlow.first(condition) }
    assertThat(state).describedAs("the session state never satisfied the condition; it is ${stateFlow.value}").isNotNull()
    return state!!
  }

  /** Changes the setting on the EDT, as the settings UI does, until the end of the test. */
  private fun setCursorShapeForTest(shape: TerminalUiSettingsManager.CursorShape) {
    val provider = TerminalOptionsProvider.instance
    val original = provider.cursorShape
    runInEdtAndWait { provider.cursorShape = shape }
    Disposer.register(disposable) { runInEdtAndWait { provider.cursorShape = original } }
  }

  /** Changes the setting on the EDT, because an open editor applies it there, until the end of the test. */
  private fun setBlinkCaretForTest(blinking: Boolean) {
    val settings = EditorSettingsExternalizable.getInstance()
    val original = settings.isBlinkCaret
    runInEdtAndWait { settings.setBlinkCaret(blinking) }
    Disposer.register(disposable) { runInEdtAndWait { settings.setBlinkCaret(original) } }
  }

  private fun csi(body: String): String = "$ESC[$body"
}
