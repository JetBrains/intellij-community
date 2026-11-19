// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.UI
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.asDisposable
import io.github.ketraterm.session.TerminalSessionState
import io.github.ketraterm.ui.swing.api.SwingTerminal
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.accessibility.Accessible
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleText
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.junit.jupiter.api.Test

@TestApplication
@SystemProperty(propertyKey = "ketraterm.experimental.enableDocumentModel", propertyValue = "true")
internal class KetraTermTerminalViewTest {
  @Test
  fun `the native PowerShell view enables the completion action`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm completion view", Dispatchers.Default)
    val project = projectFixture.get()
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true, shellCommand = listOf("pwsh.exe"),
    )
    val session = base as KetraTermTerminalSession
    val completionDisposable = Disposer.newDisposable()
    try {
      val view = withContext(Dispatchers.UI) {
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
        }
      }
      connector.feed("\u001b]1341;initialized;current_directory=2f746d70;history_path=\u0007" +
                     "\u001b]1341;prompt_started\u0007>\u001b]1341;prompt_finished\u0007Get-Ch")
      session.commandLine.first { it?.commandText == "Get-Ch" }
      withContext(Dispatchers.UI) {
        TerminalCommandCompletion.enableForTests(completionDisposable)
        val context = CustomizedDataContext.withSnapshot(
          SimpleDataContext.builder().add(PlatformDataKeys.CONTEXT_COMPONENT, view.terminal).build(),
        ) { it.uiDataSnapshot(view.component as UiDataProvider) }
        val action = ActionManager.getInstance().getAction("Terminal.CommandCompletion.Invoke")
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        assertThat(event.presentation.isEnabledAndVisible).isTrue()
      }
    }
    finally {
      withContext(Dispatchers.UI) { Disposer.dispose(completionDisposable) }
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `the Swing view applies output and preserves host input and disposal`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm view test", Dispatchers.Default)
    val project = projectFixture.get()
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    val applied = CompletableDeferred<Unit>()
    val written = ByteArrayOutputStream()
    val inputHandled = CompletableDeferred<Unit>()
    connector.responseHandler = { bytes ->
      synchronized(written) {
        written.write(bytes)
        if (written.toString(Charsets.UTF_8).endsWith("echo ready\r")) inputHandled.complete(Unit)
      }
    }
    try {
      val view = withContext(Dispatchers.UI) {
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also { view ->
          view.terminal.setSize(800, 480)
          view.outputModels.regular.addListener(scope.asDisposable(), object : TerminalOutputModelListener {
            override fun afterContentChanged(event: TerminalContentChangeEvent) {
              val model = view.outputModels.regular
              if (model.getText(model.startOffset, model.endOffset).contains("render-ready")) applied.complete(Unit)
            }
          })
        }
      }
      view.connectToSession(session)
      connector.feed("\u001b[31mrender-ready\u001b[0m\r\n")
      applied.await()
      withContext(Dispatchers.UI) {
        assertThat(view.preferredFocusableComponent).isInstanceOf(SwingTerminal::class.java)
        assertThat(view.sessionState.value).isEqualTo(TerminalViewSessionState.Running)
        assertThat(view.gridSize.columns).isGreaterThan(1)
        val image = BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try { view.terminal.paint(graphics) }
        finally { graphics.dispose() }
        val accessible = (view.preferredFocusableComponent as Accessible).accessibleContext
        assertThat(accessible.accessibleRole).isEqualTo(AccessibleRole.TEXT)
        assertThat(accessible.accessibleName).isNotBlank()
        assertThat(accessible.accessibleEditableText).isNull()
        val accessibleText = requireNotNull(accessible.accessibleText)
        fun renderedText(): String = buildString {
          repeat(accessibleText.charCount) { append(accessibleText.getAtIndex(AccessibleText.CHARACTER, it)) }
        }
        while (!renderedText().contains("render-ready")) delay(10)
        assertThat(renderedText()).contains("render-ready")
        assertThat(view.createSendTextBuilder().shouldExecute().trySend("echo ready")).isTrue()
        val clipboard = CopyPasteManager.getInstance()
        val previous = clipboard.contents
        try {
          assertThat(view.terminal.selectAll()).isTrue()
          assertThat(view.terminal.copySelectionToClipboard()).isTrue()
          while (clipboard.getContents<String>(DataFlavor.stringFlavor)?.contains("render-ready") != true) delay(10)
          assertThat(clipboard.getContents<String>(DataFlavor.stringFlavor)).contains("render-ready")
          val range = requireNotNull(view.terminal.createSelectionRange(0, 0, 6, 0))
          assertThat(view.terminal.setSelection(range)).isTrue()
          assertThat(accessibleText.selectedText).isEqualTo("render")
          assertThat(view.terminal.copySelectionToClipboard()).isTrue()
          while (clipboard.getContents<String>(DataFlavor.stringFlavor) != "render") delay(10)
          assertThat(clipboard.getContents<String>(DataFlavor.stringFlavor)).isEqualTo("render")
          view.terminal.clearSelection()
          assertThat(view.terminal.currentSelectionRange()).isNull()
        }
        finally {
          if (previous != null) clipboard.setContents(previous)
        }
      }
      inputHandled.await()
      connector.close()
      view.sessionState.first { it == TerminalViewSessionState.Terminated }
      assertThat(session.isClosed).isTrue()
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `startup input rejects impossible batches and delivers accepted text in order`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm startup input test", Dispatchers.Default)
    val project = projectFixture.get()
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    val commands = (1..16).map { "command $it" }
    val expectedBufferedInput = commands.joinToString(separator = "\r", postfix = "\r")
    val received = ByteArrayOutputStream()
    val bufferedInputWritten = CompletableDeferred<Unit>()
    val lastInputWritten = CompletableDeferred<String>()
    connector.responseHandler = { bytes ->
      received.write(bytes)
      val text = received.toString(Charsets.UTF_8)
      if (text.endsWith("\r")) {
        if (text.endsWith("command 16\r")) bufferedInputWritten.complete(Unit)
        if (text.endsWith("after attachment\r")) lastInputWritten.complete(text)
      }
    }
    try {
      val view = withContext(Dispatchers.UI) { KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope) }
      val sender = view.createSendTextBuilder().shouldExecute()
      assertThat(sender.trySend("\n".repeat(257))).isFalse()
      for (command in commands) {
        assertThat(sender.trySend(command)).isTrue()
      }
      assertThat(sender.trySend("over capacity")).isFalse()

      view.connectToSession(session)
      bufferedInputWritten.await()
      assertThat(sender.trySend("after attachment")).isTrue()
      assertThat(lastInputWritten.await()).isEqualTo(expectedBufferedInput + "after attachment\r")
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `a process that exits before attachment retains output and termination`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm late view", Dispatchers.Default)
    val project = projectFixture.get()
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    try {
      connector.feed("output-before-exit")
      connector.close()
      session.terminalSession.state.first { it is TerminalSessionState.Closed }
      session.coroutineScope.coroutineContext.job.join()
      val view = withContext(Dispatchers.UI) {
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
        }
      }
      view.sessionState.first { it == TerminalViewSessionState.Terminated }
      withContext(Dispatchers.UI) {
        val model = view.outputModels.regular
        assertThat(model.getText(model.startOffset, model.endOffset).toString()).contains("output-before-exit")
        assertThat(view.createSendTextBuilder().trySend("ignored")).isFalse()
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
