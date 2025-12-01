package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.terminal.frontend.view.completion.TerminalLookupPrefixUpdater
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.frontend.view.impl.TimedKeyEvent
import com.intellij.terminal.tests.block.util.TestCommandSpecsProvider
import com.intellij.terminal.tests.reworked.util.EchoingTerminalSession
import com.intellij.testFramework.ExtensionTestUtil
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.Assume
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_UNDEFINED
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TerminalCompletionFixture(val project: Project, val testRootDisposable: Disposable) {

  private val view: TerminalViewImpl

  val outputModel: MutableTerminalOutputModel
    get() = view.activeOutputModel() as MutableTerminalOutputModel

  init {
    Registry.get("terminal.type.ahead").setValue(true, testRootDisposable)
    TerminalCommandCompletion.enableForTests(testRootDisposable)
    // Terminal completion might still be disabled if not supported yet on some OS.
    Assume.assumeTrue(TerminalCommandCompletion.isEnabled(project))

    val terminalScope = terminalProjectScope(project).childScope("TerminalViewImpl")
    Disposer.register(testRootDisposable) {
      terminalScope.cancel()
    }
    view = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, terminalScope)
    val session = EchoingTerminalSession(coroutineScope = terminalScope.childScope("EchoingTerminalSession"))
    view.connectToSession(session)
  }

  suspend fun awaitShellIntegrationFeaturesInitialized() {
    view.shellIntegrationFeaturesInitJob.join()
  }

  suspend fun type(text: String) {
    for (c in text) {
      typeChar(c)
    }
    awaitLookupPrefixUpdated()
  }

  private fun typeChar(keyChar: Char) {
    val keyEvent = KeyEvent(view.outputEditor.component, KeyEvent.KEY_TYPED, 1, 0,
                            VK_UNDEFINED, keyChar, KeyEvent.KEY_LOCATION_UNKNOWN)
    val timedKeyEvent = TimedKeyEvent(keyEvent, TimeSource.Monotonic.markNow())
    view.outputEditorEventsHandler.keyTyped(timedKeyEvent)
  }

  suspend fun callCompletionPopup(waitForPopup: Boolean = true) {
    runActionById("Terminal.CommandCompletion.Invoke")
    if (waitForPopup) {
      awaitNewCompletionPopupOpened()
    }
  }

  suspend fun awaitNewCompletionPopupOpened() {
    withTimeout(3.seconds) {
      suspendCancellableCoroutine { continuation ->
        val showingListener = object : LookupListener {
          override fun lookupShown(event: LookupEvent) {
            event.lookup.removeLookupListener(this)
            continuation.resume(Unit)
          }
        }

        val connection = project.messageBus.connect()
        continuation.invokeOnCancellation { connection.disconnect() }
        connection.subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, newLookup ->
          if (newLookup != null) {
            connection.disconnect()
            newLookup.addLookupListener(showingListener)
          }
        })
      }
    }
  }

  fun getActiveLookup(): LookupImpl? {
    return LookupManager.getInstance(project).activeLookup as? LookupImpl
  }

  fun isLookupActive(): Boolean {
    return getActiveLookup() != null
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

  fun insertSelectedItem() {
    runActionById("Terminal.CommandCompletion.InsertSuggestion")
  }

  fun downCompletionPopup() {
    runActionById("Terminal.CommandCompletion.SelectSuggestionBelow")
  }

  fun upCompletionPopup() {
    runActionById("Terminal.CommandCompletion.SelectSuggestionAbove")
  }

  /**
   * Simulates a key press in the active completion popup.
   */
  suspend fun pressKey(keycode: Int) {
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

    val offset = outputModel.cursorOffset
    val newOffset = when (keycode) {
      KeyEvent.VK_LEFT -> offset - 1
      KeyEvent.VK_RIGHT -> offset + 1
      else -> offset
    }
    outputModel.updateCursorPosition(newOffset)

    awaitLookupPrefixUpdated()
  }

  private fun runActionById(actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId)
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, view.outputEditor)
      .add(TerminalOutputModel.DATA_KEY, outputModel)
      .add(TerminalView.DATA_KEY, view)
      .build()
    val event = AnActionEvent.createEvent(action, context, null,
                                          "", ActionUiKind.NONE, null)
    ActionUtil.updateAction(action, event)
    if (event.presentation.isEnabledAndVisible) {
      ActionUtil.performAction(action, event)
    }
  }

  /** Returns true if the output model state met the condition in the given timeout */
  suspend fun awaitOutputModelState(timeout: Duration, condition: (TerminalOutputModel) -> Boolean): Boolean {
    val result = withTimeoutOrNull(timeout) {
      suspendCancellableCoroutine { continuation ->
        val model = view.activeOutputModel()
        if (condition(model)) {
          continuation.resume(Unit)
        }

        val disposable = Disposer.newDisposable()
        continuation.invokeOnCancellation { Disposer.dispose(disposable) }

        fun check() {
          if (condition(model)) {
            Disposer.dispose(disposable)
            continuation.resume(Unit)
          }
        }

        model.addListener(disposable, object : TerminalOutputModelListener {
          override fun afterContentChanged(event: TerminalContentChangeEvent) {
            check()
          }

          override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
            check()
          }
        })
      }
    }

    return result != null
  }

  fun mockTestShellCommand(testCommandSpec: ShellCommandSpec) {
    val specsProvider: ShellCommandSpecsProvider = TestCommandSpecsProvider(
      ShellCommandSpecInfo.create(testCommandSpec, ShellCommandSpecConflictStrategy.DEFAULT)
    )
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, listOf(specsProvider), testRootDisposable)
  }

  fun setCompletionOptions(
    showPopupAutomatically: Boolean,
    showingMode: TerminalCommandCompletionShowingMode,
    parentDisposable: Disposable,
  ) {
    val options = TerminalOptionsProvider.instance
    val curShowPopupAutomatically = options.showCompletionPopupAutomatically
    val curShowingMode = options.commandCompletionShowingMode

    options.showCompletionPopupAutomatically = showPopupAutomatically
    options.commandCompletionShowingMode = showingMode

    Disposer.register(parentDisposable) {
      options.showCompletionPopupAutomatically = curShowPopupAutomatically
      options.commandCompletionShowingMode = curShowingMode
    }
  }

  private suspend fun awaitLookupPrefixUpdated() {
    val lookup = LookupManager.getInstance(project).activeLookup as? LookupImpl ?: return
    val prefixUpdater = TerminalLookupPrefixUpdater.get(lookup) ?: return
    prefixUpdater.awaitPrefixUpdated()
  }
}