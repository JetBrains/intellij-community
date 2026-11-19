// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.ide.CopyProvider
import com.intellij.ide.actions.CloseAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.TerminalSelectAllProvider
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.awt.datatransfer.DataFlavor
import javax.swing.JLabel
import javax.swing.JPanel

@TestApplication
@SystemProperty(propertyKey = "ketraterm.experimental.enableDocumentModel", propertyValue = "true")
internal class KetraTermActionsTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `terminal actions accept the Swing view snapshot without an editor`(): Unit = timeoutRunBlocking {
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val project = projectFixture.get()
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    try {
      withContext(Dispatchers.EDT) {
        val view = KetraTermTerminalView(project, JBTerminalSystemSettingsProviderBase(), scope)
        view.terminal.size = view.terminal.preferredGridSize(80, 24)
        view.connectToSession(session)
        val baseContext = SimpleDataContext.builder()
          .add(PlatformDataKeys.CONTEXT_COMPONENT, view.terminal)
          .add(PlatformDataKeys.TOOL_WINDOW, mock<ToolWindow>())
          .add(CloseAction.CloseTarget.KEY, CloseAction.CloseTarget { })
          .build()
        val context = CustomizedDataContext.withSnapshot(baseContext) { sink ->
          sink.uiDataSnapshot(view.component as UiDataProvider)
        }
        assertThat(context.getData(PlatformDataKeys.CONTEXT_COMPONENT)).isSameAs(view.terminal)
        assertThat(context.getData(TerminalView.DATA_KEY)).isSameAs(view)
        assertThat(context.getData(CommonDataKeys.EDITOR)).isNull()
        val pasteProvider = requireNotNull(context.getData(PlatformDataKeys.PASTE_PROVIDER))
        assertThat(pasteProvider.isPasteEnabled(context)).isTrue()
        val actionIds = listOf(
          "Terminal.Paste", "Terminal.PasteFromHistory", "Terminal.Find", 
          "Terminal.CloseTab", "Terminal.SwitchFocusToEditor", "Terminal.SelectAll",
          "TerminalIncreaseFontSize", "TerminalDecreaseFontSize", "TerminalResetFontSize",
        )
        for (id in actionIds) {
          val action = ActionManager.getInstance().getAction(id)
          val event = TestActionEvent.createTestEvent(action, context)
          ActionUtil.updateAction(action, event)
          assertThat(event.presentation.isEnabledAndVisible).describedAs(id).isTrue()
        }
      }
    }
    finally {
      connector.close()
      job.cancelAndJoin()
    }
  }

  @Test
  fun `terminal copy uses the Swing copy provider`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val provider = TestCopyProvider()
      val panel = object : JPanel(), TerminalPanelMarker.KetraTerm {}
      val child = JLabel()
      panel.add(child)
      val context = SimpleDataContext.builder()
        .add(PlatformDataKeys.CONTEXT_COMPONENT, child)
        .add(PlatformDataKeys.COPY_PROVIDER, provider)
        .build()
      val action = ActionManager.getInstance().getAction("Terminal.CopySelectedText")
      val event = TestActionEvent.createTestEvent(action, context)

      action.update(event)
      assertThat(event.presentation.isEnabledAndVisible).isTrue()
      action.actionPerformed(event)
      assertThat(provider.copied).isTrue()

      provider.hasSelection = false
      action.update(event)
      assertThat(event.presentation.isVisible).isTrue()
      assertThat(event.presentation.isEnabled).isFalse()
    }
  }

  @Test
  fun `terminal copy stays disabled outside a terminal`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val provider = TestCopyProvider()
      val context = SimpleDataContext.builder()
        .add(PlatformDataKeys.CONTEXT_COMPONENT, JPanel())
        .add(PlatformDataKeys.COPY_PROVIDER, provider)
        .build()
      val action = ActionManager.getInstance().getAction("Terminal.CopySelectedText")
      val event = TestActionEvent.createTestEvent(action, context)

      action.update(event)
      action.actionPerformed(event)

      assertThat(event.presentation.isVisible).isFalse()
      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(provider.copied).isFalse()
    }
  }

  @Test
  fun `terminal close uses the host close target without an editor`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val panel = object : JPanel(), TerminalPanelMarker.KetraTerm {}
      val child = JLabel()
      panel.add(child)
      var closeCount = 0
      val action = ActionManager.getInstance().getAction("Terminal.CloseTab")
      val context = SimpleDataContext.builder()
        .add(PlatformDataKeys.CONTEXT_COMPONENT, child)
        .add(CloseAction.CloseTarget.KEY, CloseAction.CloseTarget { closeCount++ })
        .build()
      val event = TestActionEvent.createTestEvent(action, context)

      action.update(event)
      assertThat(event.presentation.isEnabledAndVisible).isTrue()
      action.actionPerformed(event)
      assertThat(closeCount).isEqualTo(1)

      val noTargetContext = SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT, child)
      val noTargetEvent = TestActionEvent.createTestEvent(action, noTargetContext)
      action.update(noTargetEvent)
      assertThat(noTargetEvent.presentation.isVisible).isTrue()
      assertThat(noTargetEvent.presentation.isEnabled).isFalse()
    }
  }

  @Test
  fun `terminal close stays disabled outside a terminal`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val context = SimpleDataContext.builder()
        .add(PlatformDataKeys.CONTEXT_COMPONENT, JPanel())
        .add(CloseAction.CloseTarget.KEY, CloseAction.CloseTarget { error("The close target must not run") })
        .build()
      val action = ActionManager.getInstance().getAction("Terminal.CloseTab")
      val event = TestActionEvent.createTestEvent(action, context)

      action.update(event)

      assertThat(event.presentation.isVisible).isFalse()
      assertThat(event.presentation.isEnabled).isFalse()
    }
  }
  @Test
  fun `terminal switch focus requires a terminal and a tool window`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val terminal = object : JPanel(), TerminalPanelMarker.KetraTerm {}
      val toolWindow = mock<ToolWindow>()
      val action = ActionManager.getInstance().getAction("Terminal.SwitchFocusToEditor")
      val contexts = listOf(
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, projectFixture.get())
          .add(PlatformDataKeys.CONTEXT_COMPONENT, terminal)
          .build(),
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, projectFixture.get())
          .add(PlatformDataKeys.CONTEXT_COMPONENT, JPanel())
          .add(PlatformDataKeys.TOOL_WINDOW, toolWindow)
          .build(),
      )
      for (context in contexts) {
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        assertThat(event.presentation.isEnabledAndVisible).isFalse()
      }
    }
  }
  @Test
  fun `native action routes stay disabled in other terminal panels`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      val panel = object : JPanel(), TerminalPanelMarker {}
      val child = JLabel()
      panel.add(child)
      val context = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(PlatformDataKeys.CONTEXT_COMPONENT, child)
        .add(PlatformDataKeys.TOOL_WINDOW, mock<ToolWindow>())
        .add(CloseAction.CloseTarget.KEY, CloseAction.CloseTarget {})
        .add(PlatformDataKeys.COPY_PROVIDER, TestCopyProvider())
        .add(TerminalSelectAllProvider.DATA_KEY, TerminalSelectAllProvider {})
        .build()
      val actionIds = listOf("Terminal.CloseTab", "Terminal.SwitchFocusToEditor", "Terminal.CopySelectedText", "Terminal.SelectAll")
      for (id in actionIds) {
        val action = ActionManager.getInstance().getAction(id)
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        assertThat(event.presentation.isEnabled).describedAs(id).isFalse()
      }
    }
  }

  @Test
  fun `terminal select all uses the native selection and copy provider`(): Unit = timeoutRunBlocking {
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val project = projectFixture.get()
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val applied = CompletableDeferred<Unit>()
    try {
      val view = withContext(Dispatchers.EDT) {
        KetraTermTerminalView(project, JBTerminalSystemSettingsProviderBase(), scope).also { view ->
          view.terminal.size = view.terminal.preferredGridSize(80, 24)
          view.outputModels.regular.addListener(scope.asDisposable(), object : TerminalOutputModelListener {
            override fun afterContentChanged(event: TerminalContentChangeEvent) {
              val model = view.outputModels.regular
              if (model.getText(model.startOffset, model.endOffset).contains("native-select-all")) applied.complete(Unit)
            }
          })
          view.connectToSession(session)
        }
      }
      connector.feed("native-select-all\r\n")
      applied.await()
      withContext(Dispatchers.EDT) {
        val baseContext = SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT, view.terminal)
        val context = CustomizedDataContext.withSnapshot(baseContext) { sink ->
          sink.uiDataSnapshot(view.component as UiDataProvider)
        }
        assertThat(context.getData(PlatformDataKeys.CONTEXT_COMPONENT)).isSameAs(view.terminal)
        val provider = requireNotNull(context.getData(PlatformDataKeys.COPY_PROVIDER))
        assertThat(provider.isCopyEnabled(context)).isFalse()
        val action = ActionManager.getInstance().getAction("Terminal.SelectAll")
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        assertThat(event.presentation.isEnabledAndVisible).isTrue()
        action.actionPerformed(event)
        assertThat(provider.isCopyEnabled(context)).isTrue()
        val clipboard = CopyPasteManager.getInstance()
        val previous = clipboard.contents
        try {
          provider.performCopy(context)
          while (clipboard.getContents<String>(DataFlavor.stringFlavor)?.contains("native-select-all") != true) kotlinx.coroutines.delay(10)
          assertThat(clipboard.getContents<String>(DataFlavor.stringFlavor)).contains("native-select-all")
        }
        finally {
          if (previous != null) clipboard.setContents(previous)
        }
      }
    }
    finally {
      connector.close()
      job.cancelAndJoin()
    }
  }

  @Test
  fun `terminal select all stays disabled outside a terminal`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val context = SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT, JPanel())
      val action = ActionManager.getInstance().getAction("Terminal.SelectAll")
      val event = TestActionEvent.createTestEvent(action, context)
      action.update(event)
      assertThat(event.presentation.isEnabledAndVisible).isFalse()
    }
  }
  private class TestCopyProvider : CopyProvider {
    var hasSelection = true
    var copied = false

    override fun performCopy(dataContext: DataContext) { copied = true }
    override fun isCopyEnabled(dataContext: DataContext): Boolean = hasSelection
    override fun isCopyVisible(dataContext: DataContext): Boolean = true
  }
}
