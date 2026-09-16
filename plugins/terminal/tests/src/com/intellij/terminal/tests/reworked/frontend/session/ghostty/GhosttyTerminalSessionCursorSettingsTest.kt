// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.tests.reworked.util.awaitEvent
import com.intellij.terminal.tests.reworked.util.awaitEventAfter
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.junit.Test

/**
 * The Ghostty-backed [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession] tracks the
 * terminal's "Cursor shape" setting ([TerminalOptionsProvider]) and the editor's "Blink caret" setting
 * ([EditorSettingsExternalizable]), and pushes them into the emulator as its default cursor shape/blink.
 * This is reported to the frontend as [TerminalStateChangedEvent.state]`.cursorShape`.
 */
internal class GhosttyTerminalSessionCursorSettingsTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `initial session reflects a pre-configured custom cursor shape and blink`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.VERTICAL)
    setBlinkCaretForTest(true)

    runSessionTest { _, _, collector ->
      val initialState = collector.awaitEvent<TerminalStateChangedEvent> { true }.state
      assertThat(initialState.cursorShape).isEqualTo(CursorShapeDto.BLINK_VERTICAL_BAR)
    }
  }

  @Test
  fun `changing TerminalOptionsProvider cursor shape mid-session updates the default`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.BLOCK)
    setBlinkCaretForTest(false)

    runSessionTest { _, _, collector ->
      val initialState = collector.awaitEvent<TerminalStateChangedEvent> { true }.state
      assertThat(initialState.cursorShape).isEqualTo(CursorShapeDto.STEADY_BLOCK)

      val eventCountBeforeChange = collector.currentEventCount()
      setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.VERTICAL)

      val updatedState = collector.awaitEventAfter<TerminalStateChangedEvent>(eventCountBeforeChange) { true }.state
      assertThat(updatedState.cursorShape).isEqualTo(CursorShapeDto.STEADY_VERTICAL_BAR)
    }
  }

  @Test
  fun `changing EditorSettingsExternalizable blink caret mid-session updates the default independently of shape`() {
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.UNDERLINE)
    setBlinkCaretForTest(false)

    runSessionTest { _, _, collector ->
      val initialState = collector.awaitEvent<TerminalStateChangedEvent> { true }.state
      assertThat(initialState.cursorShape).isEqualTo(CursorShapeDto.STEADY_UNDERLINE)

      val eventCountBeforeChange = collector.currentEventCount()
      setBlinkCaretForTest(true)

      // The shape half of the DTO stays UNDERLINE: only the blink half moved.
      val updatedState = collector.awaitEventAfter<TerminalStateChangedEvent>(eventCountBeforeChange) { true }.state
      assertThat(updatedState.cursorShape).isEqualTo(CursorShapeDto.BLINK_UNDERLINE)
    }
  }

  @Test
  fun `an active DECSCUSR override survives a later default-setting change`() = runSessionTest { _, connector, collector ->
    collector.awaitEvent<TerminalStateChangedEvent> { true } // initial state, whatever the ambient settings are

    val eventCountBeforeOverride = collector.currentEventCount()
    connector.feed(csi("2 q")) // steady block, explicit
    val overriddenState = collector.awaitEventAfter<TerminalStateChangedEvent>(eventCountBeforeOverride) { true }.state
    assertThat(overriddenState.cursorShape).isEqualTo(CursorShapeDto.STEADY_BLOCK)

    val eventCountBeforeSettingChange = collector.currentEventCount()
    setCursorShapeForTest(TerminalUiSettingsManager.CursorShape.UNDERLINE)
    setBlinkCaretForTest(true)

    // Prove enough time passed for the (rejected) default push to have taken effect, if it were going to:
    // a write that must reach a fresh projection tick before this assertion can be trusted.
    connector.feed("y")
    collector.awaitEventAfter<TerminalContentUpdatedEvent>(eventCountBeforeSettingChange) { it.text.contains("y") }

    val stateEventsAfter = collector.eventsSince(eventCountBeforeSettingChange).filterIsInstance<TerminalStateChangedEvent>()
    assertThat(stateEventsAfter)
      .describedAs("a default-setting change must not override an active DECSCUSR shape")
      .isEmpty()
  }

  @Test
  fun `an unrelated TerminalOptionsProvider setting change does not alter the reported cursor default`() = runSessionTest { _, connector, collector ->
    collector.awaitEvent<TerminalStateChangedEvent> { true } // initial state

    val eventCountBeforeChange = collector.currentEventCount()
    val provider = TerminalOptionsProvider.instance
    val originalBell = provider.audibleBell
    provider.audibleBell = !originalBell
    Disposer.register(testDisposable) { provider.audibleBell = originalBell }

    // Prove enough time passed for a (would-be) cursor default push to have landed, without asserting on
    // the absence of an event this test cannot itself await.
    connector.feed("x")
    collector.awaitEventAfter<TerminalContentUpdatedEvent>(eventCountBeforeChange) { it.text.contains("x") }

    val stateEventsAfter = collector.eventsSince(eventCountBeforeChange).filterIsInstance<TerminalStateChangedEvent>()
    assertThat(stateEventsAfter)
      .describedAs("an unrelated setting must not re-push the cursor default")
      .isEmpty()
  }

  private fun setCursorShapeForTest(shape: TerminalUiSettingsManager.CursorShape) {
    val provider = TerminalOptionsProvider.instance
    val original = provider.cursorShape
    provider.cursorShape = shape
    Disposer.register(testDisposable) { provider.cursorShape = original }
  }

  private fun setBlinkCaretForTest(blinking: Boolean) {
    val settings = EditorSettingsExternalizable.getInstance()
    val original = settings.isBlinkCaret
    settings.setBlinkCaret(blinking)
    Disposer.register(testDisposable) { settings.setBlinkCaret(original) }
  }
}
