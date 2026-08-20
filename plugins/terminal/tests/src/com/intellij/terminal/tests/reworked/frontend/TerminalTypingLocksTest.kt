// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.frontend.view.impl.createTerminalKeyEventDispatcherForTests
import com.intellij.terminal.tests.reworked.util.LockKind
import com.intellij.terminal.tests.reworked.util.TerminalEdtLocksSpy
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Regression guards for terminal EDT locks during initialization and interaction: neither phase must acquire the write lock,
 * write-intent and read lock sites must stay within their respective allowlists for the relevant phase. See [TerminalEdtLocksSpy].
 *
 * When this test fails with a new site, look at the printed stack and decide: add the site to the
 * allowlist or fix the code so it does not take the lock on the EDT.
 */
@RunWith(JUnit4::class)
internal class TerminalTypingLocksTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `test EDT locks while initializing terminal`() {
    val spy = TerminalEdtLocksSpy(testRootDisposable)

    withTerminalTestFixture {
      assertNoUnexpectedLocks(spy, INIT_READ_LOCKS, INIT_WRITE_INTENT_LOCKS, "during terminal initialization")
    }
  }

  @Test
  fun `test EDT locks while typing`() {
    withTerminalTestFixture { fixture ->
      val spy = TerminalEdtLocksSpy(testRootDisposable)

      fixture.type("echo hello world")
      fixture.moveMouseOverOutput()
      assertNoUnexpectedLocks(spy, INTERACTION_READ_LOCKS, INTERACTION_WRITE_INTENT_LOCKS, "during terminal typing")
    }
  }

  private fun assertNoUnexpectedLocks(
    spy: TerminalEdtLocksSpy,
    allowedReadLocks: Set<String>,
    allowedWriteIntentLocks: Set<String>,
    operation: String,
  ) {
    val writeLocks = spy.lockUsages(LockKind.WRITE)
    assertThat(writeLocks)
      .describedAs("Write actions shouldn't be taken on the EDT $operation:\n" +
                   writeLocks.joinToString("\n\n"))
      .isEmpty()

    val readLocks = spy.lockUsages(LockKind.READ).filterNot { it.signature in allowedReadLocks }
    assertThat(readLocks)
      .describedAs("New read lock sites on the EDT $operation. " +
                   "Add them to the corresponding allowlist or avoid taking the lock:\n" +
                   readLocks.joinToString("\n\n"))
      .isEmpty()

    val writeIntentLocks = spy.lockUsages(LockKind.WRITE_INTENT).filterNot { it.signature in allowedWriteIntentLocks }
    assertThat(writeIntentLocks)
      .describedAs("New write-intent lock sites on the EDT $operation. " +
                   "Add them to the corresponding allowlist or avoid taking the lock:\n" +
                   writeIntentLocks.joinToString("\n\n"))
      .isEmpty()
  }

  private fun withTerminalTestFixture(test: suspend (TerminalTestFixture) -> Unit) {
    timeoutRunBlocking(20.seconds, context = Dispatchers.EDT) {
      ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(id = TerminalToolWindowFactory.TOOL_WINDOW_ID))

      withTerminalToolWindowManager(project) { manager ->
        TerminalTestFixture(manager, testRootDisposable).use { fixture ->
          fixture.awaitSessionStarted()
          test(fixture)
        }
      }
    }
  }

  companion object {
    private val INIT_READ_LOCKS: Set<String> = emptySet()
    private val INIT_WRITE_INTENT_LOCKS: Set<String> = setOf(
      "com.intellij.terminal.frontend.view.impl.TerminalViewImpl#configureOutputEditor",
      "org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils#createOutputEditor"
    )

    private val INTERACTION_READ_LOCKS: Set<String> = setOf(
      "com.intellij.terminal.frontend.view.completion.TerminalCommandCompletionService#obtainLookup",
      "com.intellij.terminal.frontend.view.completion.TerminalLookupManagerListener#activeLookupChanged",
      "com.intellij.terminal.frontend.view.completion.TerminalLookupManagerListener#installLookupPrefixUpdater",
      "com.intellij.terminal.frontend.view.completion.TerminalCommandCompletionProcess#<init>",
      "com.intellij.terminal.frontend.view.completion.TerminalCommandCompletionProcess#cancel",
      "org.jetbrains.plugins.terminal.block.prompt.TerminalLookupManagerListener#activeLookupChanged"
    )
    private val INTERACTION_WRITE_INTENT_LOCKS: Set<String> = emptySet()
  }
}

internal class TerminalTestFixture(
  private val manager: TerminalToolWindowTabsManager,
  parentDisposable: Disposable,
) : AutoCloseable {
  private val fixtureDisposable = Disposer.newDisposable(parentDisposable)

  private val tab = manager.createTabBuilder()
    .requestFocus(false)
    .deferSessionStartUntilUiShown(false)
    .createTab()

  private val view: TerminalViewImpl = tab.view as TerminalViewImpl
  private val editor: EditorImpl = view.outputEditor

  init {
    IdeEventQueue.getInstance().addDispatcher(
      createTerminalKeyEventDispatcherForTests(
        editor = editor,
        settings = JBTerminalSystemSettingsProvider(),
        eventsHandler = view.outputEditorKeyEventsHandler,
        disposable = fixtureDisposable,
      ),
      fixtureDisposable,
    )
  }

  init {
    editor.component.setSize(800, 600)
  }

  suspend fun awaitSessionStarted() {
    view.sessionDeferred.await()
    view.shellIntegrationDeferred.await().outputStatus.first { it == TerminalOutputStatus.TypingCommand }

    dispatchPendingEdtEvents()
  }

  private fun dispatchPendingEdtEvents() {
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  suspend fun type(text: String) {
    for (char in text) {
      val outputModel = view.outputModels.active.value
      val textBeforeCursor = outputModel.getText(outputModel.startOffset, outputModel.cursorOffset).toString()
      val textAfterCursor = outputModel.getText(outputModel.cursorOffset, outputModel.endOffset).toString()
      val expectedText = textBeforeCursor + char + textAfterCursor
      val expectedCursor = outputModel.cursorOffset + 1L

      typeChar(char)

      awaitTextAndCursor(outputModel, expectedCursor, expectedText)
    }

    dispatchPendingEdtEvents()
  }

  private fun typeChar(keyChar: Char) {
    val press = KeyEvent(editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                         KeyEvent.getExtendedKeyCodeForChar(keyChar.code), KeyEvent.CHAR_UNDEFINED,
                         KeyEvent.KEY_LOCATION_STANDARD)
    IdeEventQueue.getInstance().dispatchEvent(press)

    val typed = KeyEvent(editor.component, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                         KeyEvent.VK_UNDEFINED, keyChar, KeyEvent.KEY_LOCATION_UNKNOWN)
    IdeEventQueue.getInstance().dispatchEvent(typed)

    val release = KeyEvent(editor.component, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                           KeyEvent.getExtendedKeyCodeForChar(keyChar.code), KeyEvent.CHAR_UNDEFINED,
                           KeyEvent.KEY_LOCATION_STANDARD)
    IdeEventQueue.getInstance().dispatchEvent(release)
  }

  private suspend fun awaitTextAndCursor(
    outputModel: TerminalOutputModel,
    expectedCursor: TerminalOffset,
    expectedText: String,
  ) {
    fun matchesTextAndCursor(): Boolean {
      val actualText = outputModel.getText(outputModel.startOffset, outputModel.endOffset).toString()
      return outputModel.cursorOffset == expectedCursor && actualText == expectedText
    }

    while (!matchesTextAndCursor()) {
      delay(50.milliseconds)
    }
  }

  fun moveMouseOverOutput() {
    val point = editor.offsetToXY(0)
    val event = MouseEvent(editor.contentComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                           point.x, point.y, 1, false)

    editor.contentComponent.dispatchEvent(event)
  }

  override fun close() {
    Disposer.dispose(fixtureDisposable)
    manager.closeTab(tab)
  }
}