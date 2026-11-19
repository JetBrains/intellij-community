// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.frontend.toolwindow.impl.TerminalTabCloseListenerImpl
import com.intellij.terminal.frontend.view.TerminalShellLifecycle
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.impl.TerminalCursorObservation
import com.intellij.terminal.frontend.view.impl.TerminalShellLifecycleModel
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.junit.jupiter.api.Test

@TestApplication
internal class KetraTermLifecycleTest {
  @Test
  fun `Swing sessions emit metadata without projected content by default`(): Unit = timeoutRunBlocking {
    val scope = childScope("Native metadata", Dispatchers.Default)
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    try {
      assertThat(session.compatibilityEnabled).isFalse()
      connector.feed("\u001b]1341;initialized;current_directory=2f746d70;history_path=\u0007" +
                     "\u001b]1341;prompt_started\u0007>\u001b]1341;prompt_finished\u0007text")
      connector.close()
      val events = session.getOutputFlow().toList().flatten()
      assertThat(events).contains(TerminalPromptFinishedEvent, TerminalSessionTerminatedEvent)
      assertThat(events.filterIsInstance<TerminalContentUpdatedEvent>()).isEmpty()
      assertThat(events.filterIsInstance<TerminalCursorPositionChangedEvent>()).isEmpty()
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `lifecycle listeners preserve each command and stop after disposal`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val model = TerminalShellLifecycleModel()
      val disposable = Disposer.newDisposable()
      val events = ArrayList<String>()
      model.addListener(disposable, object : TerminalShellLifecycle.Listener {
        override fun commandStarted(command: String) {
          assertThat(model.runningCommand).isEqualTo(command)
          assertThat(model.outputStatus.value).isEqualTo(TerminalOutputStatus.ExecutingCommand)
          events.add("start $command")
        }
        override fun commandFinished(command: String?, exitCode: Int) {
          assertThat(model.runningCommand).isNull()
          events.add("finish $command $exitCode")
        }
      })
      try {
        repeat(2) {
          model.handleEvent(TerminalCommandStartedEvent("command$it"))
          model.handleEvent(TerminalCommandFinishedEvent("command$it", it, null))
        }
      }
      finally {
        Disposer.dispose(disposable)
      }
      model.handleEvent(TerminalCommandStartedEvent("ignored"))
      model.handleEvent(TerminalSessionTerminatedEvent)
      assertThat(events).containsExactly("start command0", "finish command0 0", "start command1", "finish command1 1")
      assertThat(model.outputStatus.value).isNull()
      assertThat(model.runningCommand).isNull()
    }
  }

  @Test
  fun `native lifecycle preserves close checks and command identity without a document`(): Unit = timeoutRunBlocking {
    val scope = childScope("Native view lifecycle", Dispatchers.Default)
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    try {
      val view = withContext(Dispatchers.UI) {
        KetraTermTerminalView(projectFixture.get(), JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
        }
      }
      assertThat(view.supportsOutputModel).isFalse()
      connector.feed("\u001b]1341;initialized;current_directory=2f746d70;history_path=\u0007" +
                     "\u001b]1341;prompt_started\u0007>\u001b]1341;prompt_finished\u0007")
      view.shellLifecycle.outputStatus.first { it == TerminalOutputStatus.TypingCommand }
      withContext(Dispatchers.UI) {
        assertThat(TerminalTabCloseListenerImpl.shouldConfirmClosing(view)).isFalse()
        assertThat(view.typedCommand).isEmpty()
        assertThat(view.gridGeometry.cellSize.width).isPositive()
        assertThat(view.gridGeometry.cellSize.height).isPositive()
        val observation = TerminalCursorObservation(view, scope)
        assertThat(observation.readLine()?.prefix).isEqualTo(">")
      }
      val typed = "x".repeat(90) + "👩‍💻e\u0301"
      connector.feed(typed)
      session.commandLine.first { it?.commandText == typed }
      withContext(Dispatchers.UI) {
        assertThat(TerminalCursorObservation(view, scope).readLine()?.prefix).isEqualTo(">$typed")
        assertThat(view.typedCommand).isEqualTo(typed)
      }
      connector.feed("\u001b]1341;command_started;command=67617465\u0007")
      view.shellLifecycle.outputStatus.first { it == TerminalOutputStatus.ExecutingCommand }
      withContext(Dispatchers.UI) {
        assertThat(view.shellLifecycle.runningCommand).isEqualTo("gate")
        assertThat(view.typedCommand).isNull()
        assertThat(TerminalTabCloseListenerImpl.shouldConfirmClosing(view)).isTrue()
      }
      connector.close()
      view.sessionState.first { it == TerminalViewSessionState.Terminated }
      withContext(Dispatchers.UI) {
        assertThat(view.shellLifecycle.runningCommand).isNull()
        assertThat(TerminalTabCloseListenerImpl.shouldConfirmClosing(view)).isFalse()
      }
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
