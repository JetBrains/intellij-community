// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.fus

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.terminal.frontend.fus.TerminalShortcutsPressStatistics
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

@TestApplication
internal class TerminalShortcutsPressStatisticsTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @Test
  fun `reports a shortcut with the executable of the running command`(@TestDisposable disposable: Disposable) {
    val view = shellView(shellIntegration(executedCommand = "claude --continue"))

    val reports = pressKeys(disposable, view, pressedKey(KeyEvent.VK_G, 'g', InputEvent.META_DOWN_MASK))

    assertThat(reports.single())
      .containsEntry("input_event", "Meta+G")
      .containsEntry("process_executable", "claude")
  }

  @Test
  fun `reports keys that type no character without an executable at the prompt`(@TestDisposable disposable: Disposable) {
    val view = shellView(shellIntegration(executedCommand = null))

    val reports = pressKeys(
      disposable,
      view,
      pressedKey(KeyEvent.VK_ESCAPE, Char(KeyEvent.VK_ESCAPE)),
      pressedKey(KeyEvent.VK_ENTER, '\n', InputEvent.SHIFT_DOWN_MASK),
      pressedKey(KeyEvent.VK_TAB, '\t', InputEvent.SHIFT_DOWN_MASK),
      pressedKey(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED),
    )

    assertThat(reports.map { it["input_event"] }).containsExactly("Escape", "Shift+Enter", "Shift+Tab", "Up")
    assertThat(reports).allSatisfy { assertThat(it).doesNotContainKey("process_executable") }
  }

  @Test
  fun `does not report unmodified enter tab or backspace`(@TestDisposable disposable: Disposable) {
    val view = shellView(shellIntegration(executedCommand = null))

    val reports = pressKeys(
      disposable,
      view,
      pressedKey(KeyEvent.VK_ENTER, '\n'),
      pressedKey(KeyEvent.VK_TAB, '\t'),
      pressedKey(KeyEvent.VK_BACK_SPACE, '\b'),
    )

    assertThat(reports).isEmpty()
  }

  @Test
  fun `reports an unknown executable without the shell integration`(@TestDisposable disposable: Disposable) {
    val view = shellView(shellIntegration = null)

    val reports = pressKeys(disposable, view, pressedKey(KeyEvent.VK_ESCAPE, Char(KeyEvent.VK_ESCAPE)))

    assertThat(reports.single())
      .containsEntry("input_event", "Escape")
      .containsEntry("process_executable", "<unknown>")
  }

  @Test
  fun `reports the executable of a process that runs without a shell`(@TestDisposable disposable: Disposable) {
    val view = view(TerminalProcessType.NON_SHELL, listOf("/opt/homebrew/bin/claude"), shellIntegration = null)

    val reports = pressKeys(disposable, view, pressedKey(KeyEvent.VK_ESCAPE, Char(KeyEvent.VK_ESCAPE)))

    assertThat(reports.single()).containsEntry("process_executable", "claude")
  }

  @Test
  fun `does not report typing or a modifier key alone`(@TestDisposable disposable: Disposable) {
    val view = shellView(shellIntegration(executedCommand = "claude"))

    val reports = pressKeys(
      disposable,
      view,
      pressedKey(KeyEvent.VK_A, 'a'),
      pressedKey(KeyEvent.VK_A, 'A', InputEvent.SHIFT_DOWN_MASK),
      pressedKey(KeyEvent.VK_SPACE, ' '),
      KeyEvent(Canvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'a'),
      pressedKey(KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, InputEvent.CTRL_DOWN_MASK),
      pressedKey(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED, InputEvent.SHIFT_DOWN_MASK),
    )

    assertThat(reports).isEmpty()
  }

  /**
   * Returns the data of the reported `shortcut.pressed` events.
   */
  private fun pressKeys(disposable: Disposable, view: TerminalView, vararg keyEvents: KeyEvent): List<Map<String, Any>> {
    val statistics = TerminalShortcutsPressStatistics(project, view)
    val outputModel = MutableTerminalOutputModelImpl(DocumentImpl("", true), maxOutputLength = 0)
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      for (keyEvent in keyEvents) {
        statistics.afterKeyEvent(TerminalKeyEventImpl(keyEvent, TerminalOffset.ZERO, outputModel))
      }
    }
    return events.filter { it.group.id == "terminal" && it.event.id == "shortcut.pressed" }.map { it.event.data }
  }

  private fun pressedKey(keyCode: Int, keyChar: Char, modifiersEx: Int = 0): KeyEvent {
    return KeyEvent(Canvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiersEx, keyCode, keyChar)
  }

  private fun shellView(shellIntegration: TerminalShellIntegration?): TerminalView {
    return view(TerminalProcessType.SHELL, listOf("/bin/zsh"), shellIntegration)
  }

  /**
   * Null [shellIntegration] means that the shell integration is not available, so its deferred value never completes.
   */
  private fun view(processType: TerminalProcessType, shellCommand: List<String>, shellIntegration: TerminalShellIntegration?): TerminalView {
    val startupOptions: TerminalStartupOptions = TerminalStartupOptionsImpl(
      shellCommand = shellCommand,
      workingDirectory = "/",
      envVariables = emptyMap(),
      processType = processType,
      pid = null,
    )
    val shellIntegrationDeferred = if (shellIntegration != null) {
      CompletableDeferred(shellIntegration)
    }
    else CompletableDeferred()
    return mock<TerminalView> {
      on { startupOptionsDeferred } doReturn CompletableDeferred(startupOptions)
      on { this.shellIntegrationDeferred } doReturn shellIntegrationDeferred
    }
  }

  private fun shellIntegration(executedCommand: String?): TerminalShellIntegration {
    val activeBlock = mock<TerminalCommandBlock> { on { this.executedCommand } doReturn executedCommand }
    val blocksModel = mock<TerminalBlocksModel> { on { this.activeBlock } doReturn activeBlock }
    return mock<TerminalShellIntegration> { on { this.blocksModel } doReturn blocksModel }
  }
}
