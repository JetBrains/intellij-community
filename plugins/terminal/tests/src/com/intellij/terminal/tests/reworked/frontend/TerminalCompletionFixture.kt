package com.intellij.terminal.tests.reworked.frontend

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.frontend.ReworkedTerminalView
import com.intellij.terminal.frontend.TimedKeyEvent
import com.intellij.terminal.session.TerminalBlocksModelState
import com.intellij.terminal.session.TerminalOutputBlock
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.tests.block.util.TestCommandSpecsProvider
import com.intellij.testFramework.ExtensionTestUtil
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.Companion.REWORKED_TERMINAL_COMPLETION_POPUP
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_UNDEFINED
import java.util.concurrent.CompletableFuture
import kotlin.time.TimeSource

class TerminalCompletionFixture(val project: Project, val testRootDisposable: Disposable) {

  private val view: ReworkedTerminalView

  init {
    val sessionFuture: CompletableFuture<TerminalSession> = CompletableFuture<TerminalSession>()
    view = ReworkedTerminalView(project, JBTerminalSystemSettingsProvider(), sessionFuture, null)
    Disposer.register(testRootDisposable, view)
    val terminalOutputBlock = TerminalOutputBlock(0, 0, 0, -1, 0, null)
    val blocksModelState = TerminalBlocksModelState(listOf(terminalOutputBlock), 0)
    view.blocksModel.restoreFromState(blocksModelState)
    Registry.get(REWORKED_TERMINAL_COMPLETION_POPUP).setValue(true, testRootDisposable)
    Registry.get("terminal.type.ahead").setValue(true, testRootDisposable)
  }

  fun type(text: String) {
    for (c in text) {
      typeChar(c)
    }
  }

  fun typeChar(keyChar: Char) {
    val keyEvent = KeyEvent(view.outputEditor.component, KeyEvent.KEY_TYPED, 1, 0,
                            VK_UNDEFINED, keyChar, KeyEvent.KEY_LOCATION_UNKNOWN)
    val timedKeyEvent = TimedKeyEvent(keyEvent, TimeSource.Monotonic.markNow())
    view.outputEditorEventsHandler.keyTyped(timedKeyEvent)
  }

  fun callCompletionPopup() {
    runActionById("Terminal.CommandCompletion.Gen2")
  }

  fun isLookupActive(): Boolean {
    val lookupManager = LookupManager.getInstance(project)
    return lookupManager.activeLookup != null
  }

  fun getLookupElements(): List<LookupElement> {
    val lookupManager = LookupManager.getInstance(project)
    val activeLookup = lookupManager.activeLookup
    return activeLookup?.items ?: emptyList()
  }

  fun getCurrentItem(): LookupElement? {
    val lookupManager = LookupManager.getInstance(project)
    val activeLookup = lookupManager.activeLookup
    return activeLookup?.currentItem
  }

  fun downCompletionPopup() {
    runActionById("Terminal.DownCommandCompletion")
  }

  fun upCompletionPopup() {
    runActionById("Terminal.UpCommandCompletion")
  }

  /**
   * Simulates a key press in the active completion popup.
   */
  fun pressKey(keycode: Int) {
    val keyPressEvent = KeyEvent(
      view.outputEditor.component,
      KeyEvent.KEY_PRESSED,
      System.currentTimeMillis(),
      0,
      keycode,
      KeyEvent.CHAR_UNDEFINED,
      KeyEvent.KEY_LOCATION_STANDARD
    )
    view.outputEditorEventsHandler.keyPressed(TimedKeyEvent(keyPressEvent, TimeSource.Monotonic.markNow()))
    val offset = view.outputModel.cursorOffsetState.value
    val newOffset = when (keycode) {
      KeyEvent.VK_LEFT -> offset - 1
      KeyEvent.VK_RIGHT -> offset + 1
      KeyEvent.VK_BACK_SPACE -> offset - 1
      else -> offset
    }
    view.outputModel.updateCursorPosition(view.outputModel.relativeOffset(newOffset))
  }

  private fun runActionById(actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId)
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, view.outputEditor)
      .add(TerminalOutputModel.KEY, view.outputModel)
      .build()
    val event = AnActionEvent.createEvent(action, context, null,
                                          "", ActionUiKind.NONE, null)
    ActionUtil.updateAction(action, event)
    if (event.presentation.isEnabledAndVisible) {
      ActionUtil.performAction(action, event)
    }
  }

  fun mockTestShellCommand(testCommandSpec: ShellCommandSpec) {
    val specsProvider: ShellCommandSpecsProvider = TestCommandSpecsProvider(
      ShellCommandSpecInfo.create(testCommandSpec, ShellCommandSpecConflictStrategy.DEFAULT)
    )
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, listOf(specsProvider), testRootDisposable)
  }
}