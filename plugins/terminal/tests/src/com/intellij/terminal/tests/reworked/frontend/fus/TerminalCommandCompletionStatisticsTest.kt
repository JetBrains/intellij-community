// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.fus

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.fus.TerminalCommandCompletionStatistics
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Canvas
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(30)
internal class TerminalCommandCompletionStatisticsTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @Test
  fun `reports command completion metrics`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git status")
      updateCursor("git status".length)
      recordTextChange()
      recordKeyTypedEvent()
      recordBackspaceEvent()
      recordPopupInserted(7)
      recordInlineInserted(3)
      startCommand("git status")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(0.7, data["ratio_of_popup_completion"])
    assertEquals(0.3, data["ratio_of_inline_completion"])
    assertEquals(0.1, data["typing_ratio"])
    assertEquals(0.1, data["backspaces_ratio"])
    assertEquals(16, data["rounded_total_command_inserted_length"])
    assertTrue(data["rounded_typing_time"] is Long)
  }

  @Test
  fun `reports metrics for typed characters`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("g")
      updateCursor(1)
      recordTextChange()
      recordKeyTypedEvent()

      updateCommandText("gi")
      updateCursor(2)
      recordTextChange()
      recordKeyTypedEvent()

      updateCommandText("git")
      updateCursor(3)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("git")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(1.0, data["typing_ratio"])
    assertEquals(0.0, data["backspaces_ratio"])
    assertEquals(4, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `continues counting typing after a backspace`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("a")
      updateCursor(1)
      recordTextChange()
      recordKeyTypedEvent()

      updateCommandText("ab")
      updateCursor(2)
      recordTextChange()
      recordKeyTypedEvent()

      recordBackspaceEvent()
      updateCommandText("a")
      updateCursor(1)
      recordTextChange()

      updateCommandText("ac")
      updateCursor(2)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("ac")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(1.0, data["typing_ratio"])
    assertEquals(1.0 / 3, data["backspaces_ratio"])
    assertEquals(4, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `reports repeated popup and inline completions`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("g")
      updateCursor(1)
      recordTextChange()
      recordKeyTypedEvent()

      updateCommandText("git")
      updateCursor(3)
      recordTextChange()
      recordPopupInserted(2)

      updateCommandText("git status")
      updateCursor(10)
      recordTextChange()
      recordInlineInserted(7)

      updateCommandText("git status --short")
      updateCursor(18)
      recordTextChange()
      recordPopupInserted(8)

      updateCommandText("git status --short --verbose")
      updateCursor(28)
      recordTextChange()
      recordInlineInserted(10)
      startCommand("git status --short --verbose")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(10.0 / 28, data["ratio_of_popup_completion"])
    assertEquals(17.0 / 28, data["ratio_of_inline_completion"])
    assertEquals(32, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `reports text confirmed by cursor movement after a suggestion`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()

      updateCommandText("git status")
      recordTextChange()

      updateCursor(4)
      recordTextChange()
      updateCursor(5)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("git s")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(8, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `reports an accepted shell suggestion`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()

      updateCommandText("git status")
      recordTextChange()

      updateCursor(4)
      recordTextChange()
      updateCursor("git status".length)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("git status")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(16, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `does not count a canceled shell suggestion`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()

      updateCommandText("git status")
      recordTextChange()

      updateCursor(4)
      recordTextChange()
      updateCommandText("git ")
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("git ")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(4, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `reports an accepted larger shell suggestion`(@TestDisposable disposable: Disposable) {
    val suggestion = "git status"
    val largerSuggestion = "git status --short"
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()

      updateCommandText(suggestion)
      recordTextChange()

      updateCommandText(largerSuggestion)
      recordTextChange()
      updateCursor(largerSuggestion.length)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand(largerSuggestion)
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(32, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `clears previous metrics when command text becomes empty`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()
      repeat(2) { recordKeyTypedEvent() }
      recordBackspaceEvent()
      recordPopupInserted(2)
      recordInlineInserted(1)

      updateCommandText("")
      updateCursor(0)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(0.0, data["ratio_of_popup_completion"])
    assertEquals(0.0, data["ratio_of_inline_completion"])
    assertEquals(0.0, data["typing_ratio"])
    assertEquals(0.0, data["backspaces_ratio"])
    assertEquals(0, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `does not reduce inserted length after deleting text`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()
      recordKeyTypedEvent()

      recordBackspaceEvent()
      updateCommandText("gi")
      updateCursor(2)
      recordTextChange()
      startCommand("gi")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(1.0 / 3, data["typing_ratio"])
    assertEquals(1.0 / 3, data["backspaces_ratio"])
    assertEquals(4, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `reports text inserted in the middle of a command`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("abcd")
      updateCursor(4)
      recordTextChange()

      updateCursor(2)
      recordTextChange()
      updateCommandText("abXcd")
      updateCursor(3)
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("abXcd")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(8, data["rounded_total_command_inserted_length"])
  }

  @Test
  fun `does not count unchanged model and cursor state twice`(@TestDisposable disposable: Disposable) {
    val events = doTest(disposable) {
      updateCommandText("git")
      updateCursor(3)
      recordTextChange()
      recordTextChange()
      recordKeyTypedEvent()
      startCommand("git")
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(4, data["rounded_total_command_inserted_length"])
  }

  private fun doTest(disposable: Disposable, test: Fixture.() -> Unit): List<LogEvent> {
    return FUCollectorTestCase.collectLogEvents(disposable) {
      timeoutRunBlocking(timeout = 10.seconds) {
        Fixture(project).use { fixture ->
          withContext(Dispatchers.EDT) {
            fixture.initializePrompt()
            fixture.test()
          }
        }
      }
    }
  }

  private class Fixture(project: Project) : AutoCloseable {
    private val scope: CoroutineScope = terminalProjectScope(project).childScope("TerminalCommandCompletionStatisticsTest")
    private val outputModel = MutableTerminalOutputModelImpl(DocumentImpl("", true), maxOutputLength = 0)
    private val shellIntegration = TerminalShellIntegrationImpl(
      outputModel,
      TerminalSessionModelImpl(),
      scope,
      LocalEelDescriptor,
      ShellName.of("unknown"),
    )
    private lateinit var keyEventsListener: TerminalKeyEventsListener
    private val statistics = TerminalCommandCompletionStatistics.install(
      project = project,
      shellIntegration = shellIntegration,
      outputModel = outputModel,
      isCursorVisible = { true },
      registerKeyEventsListener = { _, listener -> keyEventsListener = listener },
      coroutineScope = scope,
    )

    fun initializePrompt() {
      shellIntegration.onPromptStarted(TerminalOffset.ZERO)
      shellIntegration.onPromptFinished(TerminalOffset.ZERO)
      outputModel.updateContent(0, "")
      outputModel.updateCursorPosition(0, 0)
    }

    fun updateCommandText(text: String) {
      outputModel.updateContent(0, text)
    }

    fun updateCursor(offset: Int) {
      outputModel.updateCursorPosition(0, offset)
    }

    fun recordTextChange() {
      statistics.recordCommandTextChanged(outputModel)
    }

    fun recordKeyTypedEvent() {
      keyEventsListener.afterKeyEvent(
        TerminalKeyEventImpl(
          KeyEvent(
            Canvas(),
            KeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_UNDEFINED,
            'a',
          ),
          TerminalOffset.ZERO,
        )
      )
    }

    fun recordBackspaceEvent() {
      keyEventsListener.afterKeyEvent(
        TerminalKeyEventImpl(
          KeyEvent(
            Canvas(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_BACK_SPACE,
            KeyEvent.CHAR_UNDEFINED,
          ),
          TerminalOffset.ZERO,
        )
      )
    }

    fun recordPopupInserted(length: Int) {
      statistics.recordPopupInserted(length)
    }

    fun recordInlineInserted(length: Int) {
      statistics.recordInlineInserted(length)
    }

    fun startCommand(command: String) {
      shellIntegration.onCommandStarted(TerminalOffset.of(command.length.toLong()), command)
    }

    override fun close() {
      scope.cancel()
    }
  }
}
