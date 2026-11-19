// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(io.github.ketraterm.ui.swing.suggestion.ExperimentalShellSuggestionsApi::class)

package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ketraterm.createKetraTermTerminalSession
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.frontend.view.impl.redispatchTerminalKeyEvent
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.jediterm.core.util.TermSize
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import io.github.ketraterm.ui.swing.host.SwingTerminalOverlayPane
import io.github.ketraterm.ui.swing.host.SwingTerminalSearchBar
import io.github.ketraterm.ui.swing.host.SwingTerminalSearchAction
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionAcceptanceResult
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionTrigger
import io.github.ketraterm.shell.launch.TerminalShellLaunch
import io.github.ketraterm.workspace.TerminalProfile
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Base64
import javax.swing.JTextField
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
internal class KetraTermNativeControlsTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `native suggestions navigate and reject input after the request`(stale: Boolean, @TempDir scripts: Path): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm native suggestions", Dispatchers.Default)
    val project = projectFixture.get()
    val (session, connector) = nativeSession(scope, scripts)
    val completionDisposable = Disposer.newDisposable()
    val written = ByteArrayOutputStream()
    connector.responseHandler = { bytes ->
      val requestId = completionRequestId(bytes)
      if (requestId != null) connector.feed(completionResult(requestId))
      else synchronized(written) { written.write(bytes) }
    }
    try {
      val view = withContext(Dispatchers.UI) {
        TerminalCommandCompletion.enableForTests(completionDisposable)
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
        }
      }
      connector.feed(nativePrompt(scripts) + "Get-Ch")
      session.terminalSession.activeShellCommandLineRevision.first {
        session.terminalSession.activeShellCommandLine()?.commandText == "Get-Ch"
      }
      assertThat(session.terminalSession.shellCompletionAvailable).isTrue()
      while (!withContext(Dispatchers.UI) { view.isCompletionAvailable() }) delay(10)
      withContext(Dispatchers.UI) { assertThat(view.invokeCompletion()).describedAs("Completion request admission").isTrue() }
      val visible = withTimeoutOrNull(3000) {
        while (!withContext(Dispatchers.UI) { view.terminal.currentShellSuggestionState().visible }) delay(10)
        true
      }
      assertThat(visible).describedAs("Native completion popup").isTrue()
      withContext(Dispatchers.UI) {
        assertThat(view.terminal.inlineShellSuggestionPresentation.value).isNull()
        key(view, KeyEvent.VK_DOWN)
        assertThat(view.terminal.currentShellSuggestionState().selectedSuggestion?.replacementText).isEqualTo("Get-ChildItem")
        if (stale) session.terminalSession.submitBytes(byteArrayOf('x'.code.toByte()))
        key(view, KeyEvent.VK_ENTER)
        assertThat(view.terminal.currentShellSuggestionState().visible).isFalse()
      }
      val expected = if (stale) "x\r" else "\u007f".repeat(6) + "Get-ChildItem"
      while (synchronized(written) { written.size() < expected.length }) delay(10)
      assertThat(synchronized(written) { written.toString(Charsets.UTF_8) }).isEqualTo(expected)
    }
    finally {
      withContext(Dispatchers.UI) { Disposer.dispose(completionDisposable) }
      connector.close()
      scope.cancel()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["bash", "pwsh.exe"])
  fun `inline completion works without automatic popup`(shell: String): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm inline suggestions", Dispatchers.Default)
    val project = projectFixture.get()
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true, shellCommand = listOf(shell),
    )
    val session = base as KetraTermTerminalSession
    val disposable = Disposer.newDisposable()
    val options = TerminalOptionsProvider.instance
    val previousAutomatic = options.showCompletionPopupAutomatically
    try {
      val view = withContext(Dispatchers.UI) {
        TerminalCommandCompletion.enableForTests(disposable)
        options.showCompletionPopupAutomatically = false
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
        }
      }
      connector.feed("\u001b]1341;initialized;current_directory=2f746d70;history_path=\u0007" +
                     "\u001b]1341;prompt_started\u0007>\u001b]1341;prompt_finished\u0007git checkou")
      session.commandLine.first { it?.commandText == "git checkou" }
      while (!withContext(Dispatchers.UI) { view.isCompletionAvailable() }) delay(10)
      withContext(Dispatchers.UI) {
        val terminal = view.terminal
        val focus = FocusEvent(terminal, FocusEvent.FOCUS_GAINED)
        terminal.focusListeners.forEach { it.focusGained(focus) }
        terminal.requestActiveShellSuggestions(SwingShellSuggestionTrigger.AUTOMATIC)
      }
      view.terminal.inlineShellSuggestionPresentation.first { it != null }
      withContext(Dispatchers.UI) {
        assertThat(view.terminal.currentShellSuggestionState().visible).isFalse()
        assertThat(view.terminal.acceptInlineShellSuggestion()).isEqualTo(SwingShellSuggestionAcceptanceResult.ACCEPTED)
        assertThat(view.terminal.inlineShellSuggestionPresentation.value).isNull()
      }
    }
    finally {
      withContext(Dispatchers.UI) {
        options.showCompletionPopupAutomatically = previousAutomatic
        Disposer.dispose(disposable)
      }
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `a late native completion cannot replace edited input`(@TempDir scripts: Path): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm request context", Dispatchers.Default)
    val project = projectFixture.get()
    val (session, connector) = nativeSession(scope, scripts)
    val completionDisposable = Disposer.newDisposable()
    val written = ByteArrayOutputStream()
    val requested = CompletableDeferred<Long>()
    connector.responseHandler = { bytes ->
      val requestId = completionRequestId(bytes)
      if (requestId != null) requested.complete(requestId)
      else synchronized(written) { written.write(bytes) }
    }
    try {
      val view = withContext(Dispatchers.UI) {
        TerminalCommandCompletion.enableForTests(completionDisposable)
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
        }
      }
      connector.feed(nativePrompt(scripts) + "Get-Ch")
      session.terminalSession.activeShellCommandLineRevision.first {
        session.terminalSession.activeShellCommandLine()?.commandText == "Get-Ch"
      }
      assertThat(session.terminalSession.shellCompletionAvailable).isTrue()
      while (!withContext(Dispatchers.UI) { view.isCompletionAvailable() }) delay(10)
      withContext(Dispatchers.UI) { assertThat(view.invokeCompletion()).describedAs("Completion request admission").isTrue() }
      val requestId = withTimeoutOrNull(2000) { requested.await() }
      assertThat(requestId).describedAs("Native completion request").isNotNull()
      session.terminalSession.submitBytes(byteArrayOf('x'.code.toByte()))
      connector.feed(completionResult(requireNotNull(requestId)) + "x")
      val echoed = withTimeoutOrNull(2000) {
        session.terminalSession.activeShellCommandLineRevision.first {
          session.terminalSession.activeShellCommandLine()?.commandText == "Get-Chx"
        }
      }
      assertThat(echoed).withFailMessage("Missing input echo: %s", session.terminalSession.activeShellCommandLine()).isNotNull()
      withContext(Dispatchers.UI) {
        assertThat(view.terminal.currentShellSuggestionState().visible).isFalse()
      }
      while (synchronized(written) { written.size() == 0 }) delay(10)
      assertThat(synchronized(written) { written.toString(Charsets.UTF_8) }).isEqualTo("x")
    }
    finally {
      withContext(Dispatchers.UI) { Disposer.dispose(completionDisposable) }
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `the library search bar observes results without changing the grid`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm native search", Dispatchers.Default)
    val project = projectFixture.get()
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    var search: SwingTerminalSearchBar? = null
    try {
      val terminal = withContext(Dispatchers.UI) {
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also { it.connectToSession(session) }.terminal
      }
      val generation = session.terminalSession.renderGeneration.value
      connector.feed("Needle alpha needle\r\n")
      session.terminalSession.renderGeneration.first { it > generation }
      withContext(Dispatchers.UI) {
        search = SwingTerminalSearchBar(terminal)
        val pane = SwingTerminalOverlayPane(terminal, search!!.component)
        pane.setSize(800, 480)
        pane.doLayout()
        val before = terminal.visibleGridSize()
        search!!.open()
        pane.doLayout()
        assertThat(terminal.visibleGridSize()).isEqualTo(before)
        UIUtil.findComponentOfType(search!!.component, JTextField::class.java)!!.text = "needle"
      }
      terminal.searchState.first { !it.isSearching && it.resultCount == 2 }
      withContext(Dispatchers.UI) {
        assertThat(search!!.performAction(SwingTerminalSearchAction.TOGGLE_MATCH_CASE)).isTrue()
      }
      terminal.searchState.first { !it.isSearching && it.resultCount == 1 }
      withContext(Dispatchers.UI) {
        search!!.close()
        assertThat(search!!.isOpen()).isFalse()
        assertThat(terminal.currentSearchState().query).isEmpty()
      }
    }
    finally {
      withContext(Dispatchers.UI) { search?.close() }
      connector.close()
      scope.cancel()
    }
  }

  private fun key(view: KetraTermTerminalView, code: Int) {
    for (id in intArrayOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
      redispatchTerminalKeyEvent(view.terminal, KeyEvent(view.terminal, id, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
    }
  }

  private fun nativeSession(scope: CoroutineScope, scripts: Path): Pair<KetraTermTerminalSession, LoopbackTtyConnector> {
    val launch = TerminalShellLaunch.prepare(TerminalProfile("test", "test", listOf("pwsh")), scriptDirectory = scripts)
    val connector = LoopbackTtyConnector()
    val options = ShellStartupOptions.Builder().shellCommand(listOf("pwsh")).initialTermSize(TermSize(80, 24)).build()
    return createKetraTermTerminalSession(
      projectFixture.get(), connector, options, JBTerminalSystemSettingsProvider(), scope, shellLaunch = launch,
    ) to connector
  }

  private fun nativePrompt(directory: Path): String =
    "\u001b]7;${directory.toUri()}\u0007\u001b]133;A\u0007" +
    "\u001b]633;KetraTermCompletion;3;Ready;$COMPLETION_EPOCH\u0007>\u001b]133;B\u0007"

  private fun completionRequestId(bytes: ByteArray): Long? {
    val keys = bytes.toString(Charsets.UTF_8).split("\u001b[24~").drop(1)
    if (keys.size != 21 || keys.first() != "q" || keys.last() != "e") return null
    return keys.subList(1, 20).joinToString("").toLong()
  }

  private fun completionResult(requestId: Long): String {
    fun encoded(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())
    return "\u001b]633;KetraTermCompletion;3;Result;$COMPLETION_EPOCH;$requestId;Ok;6;0;6;${encoded("Get-Ch")};;1;" +
           "Command;${encoded("Get-ChildItem")};${encoded("Get-ChildItem")};\u0007"
  }

  companion object {
    private const val COMPLETION_EPOCH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val projectFixture = projectFixture()
  }
}
