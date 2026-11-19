// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.UI
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.impl.TerminalEventDispatcher
import com.intellij.terminal.frontend.view.impl.TerminalKeyEventsHandler
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

@TestApplication
@SystemProperty(propertyKey = "ketraterm.experimental.enableDocumentModel", propertyValue = "false")
internal class KetraTermBufferActionsTest {
  @Test
  fun `native selection supplies the search query without a document`(): Unit = withTerminal { view, connector ->
    withContext(Dispatchers.UI) {
      assertThat(context(view).getData(PlatformDataKeys.PREDEFINED_TEXT)).isNull()
    }
    val text = "retained query\r\n".repeat(60) + "wrapped query ".repeat(10) + "你好 e\u0301 query-tail"
    connector.feed(text)
    awaitUi {
      view.terminal.selectAll()
      view.terminal.selectedText()?.contains("query-tail") == true
    }
    withContext(Dispatchers.UI) {
      val selected = view.terminal.selectedText()
      assertThat(selected).contains("retained query", "wrapped query", "你好 e\u0301 query-tail")
      val snapshot = context(view)
      assertThat(snapshot.getData(PlatformDataKeys.PREDEFINED_TEXT)).isEqualTo(selected)
      assertThat(snapshot.getData(CommonDataKeys.EDITOR)).isNull()
      view.terminal.clearSelection()
      assertThat(context(view).getData(PlatformDataKeys.PREDEFINED_TEXT)).isNull()
    }
  }

  @Test
  fun `custom clear and scroll shortcuts reach the IDE action system`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val manager = KeymapManagerEx.getInstanceEx()
      val previous = manager.activeKeymap
      val keymap = previous.deriveKeymap("KetraTerm buffer action test")
      val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke("ctrl alt shift F12"), null)
      for (id in keymap.getActionIds(shortcut)) keymap.removeShortcut(id, shortcut)
      val disposable = Disposer.newDisposable()
      val component = JPanel()
      var forwarded = 0
      val handler = object : TerminalKeyEventsHandler {
        override fun keyPressed(e: KeyEvent) { forwarded++ }
        override fun keyTyped(e: KeyEvent) { forwarded++ }
        override fun keyReleased(e: KeyEvent) { forwarded++ }
      }
      val settings = object : JBTerminalSystemSettingsProviderBase() {
        override fun overrideIdeShortcuts(): Boolean = true
      }
      val dispatcher = TerminalEventDispatcher(projectFixture.get(), component, settings, handler, disposable)
      try {
        manager.activeKeymap = keymap
        dispatcher.registerIfNeeded()
        for (id in listOf("Terminal.ClearBuffer", "Terminal.LineUp", "Terminal.LineDown", "Terminal.PageUp", "Terminal.PageDown")) {
          keymap.removeAllActionShortcuts(id)
          keymap.addShortcut(id, shortcut)
          val event = KeyEvent(
            component, KeyEvent.KEY_PRESSED, 0, shortcut.firstKeyStroke.modifiers, KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED,
          )
          dispatcher.dispatch(event)
          assertThat(forwarded).describedAs(id).isZero()
          assertThat(event.isConsumed).isFalse()
          keymap.removeAllActionShortcuts(id)
        }
        dispatcher.dispatch(KeyEvent(
          component, KeyEvent.KEY_PRESSED, 0, shortcut.firstKeyStroke.modifiers, KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED,
        ))
        assertThat(forwarded).isEqualTo(1)
      }
      finally {
        dispatcher.unregisterIfRegistered()
        manager.activeKeymap = previous
        Disposer.dispose(disposable)
      }
    }
  }

  @Test
  fun `line and page actions scroll native history in both directions`(): Unit = withTerminal { view, connector ->
    connector.feed((1..160).joinToString("\r\n") { "line-$it" } + "\r\n")
    awaitUi { view.terminal.viewportState().historySize > 100 }
    invoke(view, "Terminal.LineUp")
    awaitOffset(view, 1.0)
    invoke(view, "Terminal.LineDown")
    awaitOffset(view, 0.0)
    val rows = withContext(Dispatchers.UI) { view.terminal.viewportState().visibleRows }
    invoke(view, "Terminal.PageUp")
    awaitOffset(view, rows.toDouble())
    invoke(view, "Terminal.PageDown")
    awaitOffset(view, 0.0)
    invoke(view, "Terminal.LineDown")
    awaitOffset(view, 0.0)
    withContext(Dispatchers.UI) { view.terminal.scrollToScrollbackOffset(Int.MAX_VALUE) }
    awaitUi {
      val state = view.terminal.viewportState()
      state.scrollbackOffset == state.historySize.toDouble()
    }
    invoke(view, "Terminal.PageUp")
    awaitUi {
      val state = view.terminal.viewportState()
      state.scrollbackOffset == state.historySize.toDouble()
    }
  }

  @Test
  fun `clear removes native history without shell input and accepts later output`(): Unit = withTerminal { view, connector ->
    connector.responseHandler = { error("A local clear must not write to the shell") }
    connector.feed((1..100).joinToString("\r\n") { "before-$it" } + "\r\n")
    awaitUi { view.terminal.viewportState().historySize > 50 }
    invoke(view, "Terminal.PageUp")
    awaitUi { view.terminal.viewportState().scrollbackOffset > 0 }
    invoke(view, "Terminal.ClearBuffer")
    awaitUi {
      val state = view.terminal.viewportState()
      state.historySize == 0 && state.isAtLiveViewport
    }
    connector.feed((1..100).joinToString("\r\n") { "after-$it" } + "\r\n")
    awaitUi { view.terminal.viewportState().historySize > 50 }
    invoke(view, "Terminal.LineUp")
    awaitOffset(view, 1.0)
  }

  @Test
  fun `clear stays disabled before a session connects`(): Unit = timeoutRunBlocking {
    val scope = childScope("Unbound terminal actions", Dispatchers.Default)
    try {
      withContext(Dispatchers.UI) {
        val view = KetraTermTerminalView(projectFixture.get(), JBTerminalSystemSettingsProvider(), scope)
        val action = ActionManager.getInstance().getAction("Terminal.ClearBuffer")
        val event = TestActionEvent.createTestEvent(action, context(view))
        action.update(event)
        assertThat(event.presentation.isEnabled).isFalse()
        action.actionPerformed(event)
      }
    }
    finally {
      scope.cancel()
    }
  }

  private fun withTerminal(block: suspend (KetraTermTerminalView, LoopbackTtyConnector) -> Unit): Unit = timeoutRunBlocking {
    val scope = childScope("Native buffer actions", Dispatchers.Default)
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    try {
      val view = withContext(Dispatchers.UI) {
        KetraTermTerminalView(projectFixture.get(), JBTerminalSystemSettingsProvider(), scope).also {
          it.terminal.size = it.terminal.preferredGridSize(80, 24)
          it.connectToSession(session)
          assertThat(it.supportsOutputModel).isFalse()
          assertThat(context(it).getData(CommonDataKeys.EDITOR)).isNull()
        }
      }
      block(view, connector)
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  private suspend fun invoke(view: KetraTermTerminalView, id: String) {
    withContext(Dispatchers.UI) {
      val action = ActionManager.getInstance().getAction(id)
      val event = TestActionEvent.createTestEvent(action, context(view))
      action.update(event)
      assertThat(event.presentation.isEnabledAndVisible).describedAs(id).isTrue()
      action.actionPerformed(event)
    }
  }

  private fun context(view: KetraTermTerminalView) = CustomizedDataContext.withSnapshot(
    SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT, view.terminal),
  ) { sink -> sink.uiDataSnapshot(view.component as UiDataProvider) }

  private suspend fun awaitOffset(view: KetraTermTerminalView, expected: Double) {
    awaitUi { view.terminal.viewportState().scrollbackOffset == expected }
  }

  private suspend fun awaitUi(condition: () -> Boolean) {
    while (!withContext(Dispatchers.UI) { condition() }) delay(10)
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
