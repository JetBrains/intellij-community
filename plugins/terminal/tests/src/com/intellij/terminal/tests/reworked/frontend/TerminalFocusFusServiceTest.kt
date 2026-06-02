// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.fus.createTerminalFocusFusServiceForTest
import com.intellij.terminal.frontend.fus.reduceTerminalFocusEventForTest
import com.intellij.terminal.frontend.fus.terminalFocusLogActionForTest
import com.intellij.terminal.frontend.fus.updateTerminalFocusFusStateForTest
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.fus.TerminalNonToolWindowFocus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.event.FocusEvent
import java.awt.event.WindowEvent
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
internal class TerminalFocusFusServiceTest {
  @Test
  fun `service does not retain focused swing component`() {
    withService { service ->
      val focusedComponent = FocusedTestPanel()

      assertThat(updateTerminalFocusFusStateForTest(service, hasFocusedWindow = true, focusedComponent = focusedComponent))
        .isEqualTo(TerminalNonToolWindowFocus.OTHER_COMPONENT.name)

      LeakHunter.checkLeak(service, FocusedTestPanel::class.java) { true }
    }
  }

  @Test
  fun `terminal marker is classified as terminal focus`() {
    withService { service ->
      assertThat(updateTerminalFocusFusStateForTest(service, hasFocusedWindow = true, focusedComponent = TestTerminalPanel()))
        .isEqualTo(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    }
  }

  @Test
  fun `ordinary component is classified as other component`() {
    withService { service ->
      assertThat(updateTerminalFocusFusStateForTest(service, hasFocusedWindow = true, focusedComponent = JPanel()))
        .isEqualTo(TerminalNonToolWindowFocus.OTHER_COMPONENT.name)
    }
  }

  @Test
  fun `component without focused window is classified as other application`() {
    withService { service ->
      assertThat(updateTerminalFocusFusStateForTest(service, hasFocusedWindow = false, focusedComponent = TestTerminalPanel()))
        .isEqualTo(TerminalNonToolWindowFocus.OTHER_APPLICATION.name)
    }
  }

  @Test
  fun `window gained focus uses the current focus owner, not the event source`() {
    val owner = JPanel()
    assertThat(reduceTerminalFocusEventForTest(WindowEvent.WINDOW_GAINED_FOCUS, eventSource = JPanel(), focusedWindowPresent = false, focusOwner = owner))
      .isEqualTo(true to owner)
  }

  @Test
  fun `window lost focus clears the focused component`() {
    assertThat(reduceTerminalFocusEventForTest(WindowEvent.WINDOW_LOST_FOCUS, eventSource = JPanel(), focusedWindowPresent = true, focusOwner = JPanel()))
      .isEqualTo(false to null)
  }

  @Test
  fun `focus gained uses the event source and the live window presence`() {
    val source = JPanel()
    assertThat(reduceTerminalFocusEventForTest(FocusEvent.FOCUS_GAINED, eventSource = source, focusedWindowPresent = true, focusOwner = JPanel()))
      .isEqualTo(true to source)
    assertThat(reduceTerminalFocusEventForTest(FocusEvent.FOCUS_GAINED, eventSource = source, focusedWindowPresent = false, focusOwner = JPanel()))
      .isEqualTo(false to source)
  }

  @Test
  fun `unrelated focus event is ignored`() {
    assertThat(reduceTerminalFocusEventForTest(FocusEvent.FOCUS_LOST, eventSource = JPanel(), focusedWindowPresent = true, focusOwner = JPanel()))
      .isNull()
  }

  @Test
  fun `entering the terminal logs the previous focus`() {
    assertThat(terminalFocusLogActionForTest(previousTerminalFocused = false, previousFus = "EDITOR", nextTerminalFocused = true, nextFus = "Terminal"))
      .isEqualTo("ENTER" to "EDITOR")
  }

  @Test
  fun `entering the terminal as the first focused component is not logged`() {
    assertThat(terminalFocusLogActionForTest(previousTerminalFocused = false, previousFus = null, nextTerminalFocused = true, nextFus = "Terminal"))
      .isEqualTo("ENTER" to null)
  }

  @Test
  fun `leaving the terminal logs the next focus`() {
    assertThat(terminalFocusLogActionForTest(previousTerminalFocused = true, previousFus = "Terminal", nextTerminalFocused = false, nextFus = "EDITOR"))
      .isEqualTo("LEAVE" to "EDITOR")
  }

  @Test
  fun `leaving the terminal to an unknown destination is flagged`() {
    assertThat(terminalFocusLogActionForTest(previousTerminalFocused = true, previousFus = "Terminal", nextTerminalFocused = false, nextFus = null))
      .isEqualTo("LEAVE" to null)
  }

  @Test
  fun `transitions that do not cross the terminal boundary produce no action`() {
    assertThat(terminalFocusLogActionForTest(previousTerminalFocused = false, previousFus = "EDITOR", nextTerminalFocused = false, nextFus = "OTHER_COMPONENT"))
      .isEqualTo("NONE" to null)
    assertThat(terminalFocusLogActionForTest(previousTerminalFocused = true, previousFus = "Terminal", nextTerminalFocused = true, nextFus = "Terminal"))
      .isEqualTo("NONE" to null)
  }

  private fun withService(action: (Any) -> Unit) = runBlocking {
    val scope = childScope("TerminalFocusFusServiceTest")
    try {
      action(createTerminalFocusFusServiceForTest(scope))
    }
    finally {
      scope.cancel()
    }
  }

  private class FocusedTestPanel : JPanel()

  private class TestTerminalPanel : JPanel(), TerminalPanelMarker
}
