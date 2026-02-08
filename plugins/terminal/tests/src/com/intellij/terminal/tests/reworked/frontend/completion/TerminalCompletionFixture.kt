package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
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
import com.intellij.terminal.frontend.view.completion.TerminalCommandCompletionService
import com.intellij.terminal.frontend.view.completion.TerminalLookupPrefixUpdater
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.frontend.view.impl.TimedKeyEvent
import com.intellij.terminal.tests.block.util.TestCommandSpecsProvider
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.junit.Assume
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_UNDEFINED
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class TerminalCompletionFixture(
  private val project: Project,
  session: TerminalSession,
  private val coroutineScope: CoroutineScope,
) {
  val view: TerminalViewImpl

  val outputModel: MutableTerminalOutputModel
    get() = view.activeOutputModel() as MutableTerminalOutputModel

  init {
    val parentDisposable = coroutineScope.asDisposable()
    Registry.get("terminal.type.ahead").setValue(true, parentDisposable)
    TerminalCommandCompletion.enableForTests(parentDisposable)
    // Terminal completion might still be disabled if not supported yet on some OS.
    Assume.assumeTrue(TerminalCommandCompletion.isEnabled(project))

    val terminalViewScope = coroutineScope.childScope("TerminalViewImpl")
    view = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, terminalViewScope)
    view.connectToSession(session)
  }

  suspend fun awaitShellIntegrationFeaturesInitialized() {
    view.shellIntegrationFeaturesInitJob.join()
    val shellIntegration = view.shellIntegrationDeferred.await()
    shellIntegration.outputStatus.first { it == TerminalOutputStatus.TypingCommand }
  }

  suspend fun type(text: String) {
    for (c in text) {
      typeChar(c)
    }
    awaitLookupPrefixUpdated()
  }

  private fun typeChar(keyChar: Char) {
    // Key pressed event shouldn't be handled, but it is required
    // to clear the ` ignoreNextKeyTypedEvent ` state in `TerminalEventsHandlerImpl`.
    // Otherwise, the next key typed event might be ignored.
    val fakeKeyPressEvent = KeyEvent(view.outputEditor.component, KeyEvent.KEY_PRESSED, 0, 0,
                                     0, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD)
    view.outputEditorEventsHandler.keyPressed(TimedKeyEvent(fakeKeyPressEvent, TimeSource.Monotonic.markNow()))

    val keyTypedEvent = KeyEvent(view.outputEditor.component, KeyEvent.KEY_TYPED, 0, 0,
                                 VK_UNDEFINED, keyChar, KeyEvent.KEY_LOCATION_UNKNOWN)
    view.outputEditorEventsHandler.keyTyped(TimedKeyEvent(keyTypedEvent, TimeSource.Monotonic.markNow()))
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
        val connection = project.messageBus.connect()
        continuation.invokeOnCancellation { connection.disconnect() }

        val showingListener = object : LookupListener {
          override fun lookupShown(event: LookupEvent) {
            event.lookup.removeLookupListener(this)
            connection.disconnect()
            continuation.resume(Unit)
          }
        }

        connection.subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, newLookup ->
          newLookup?.addLookupListener(showingListener)
        })
      }
    }
  }

  suspend fun awaitLookupElementsEqual(vararg expected: String) {
    awaitLookupElementsSatisfy {
      it.toSet() == expected.toSet()
    }
  }

  suspend fun awaitLookupElementsSatisfy(condition: (List<String>) -> Boolean) {
    // Remember the stacktrace of calling code to log it in case of failure,
    // because at the moment of logging the stacktrace might be irrelevant (coroutines)
    val callLocationThrowable = Throwable()

    val success = withTimeoutOrNull(3.seconds) {
      suspendCancellableCoroutine { continuation ->
        val lookup = getActiveLookup() ?: error("No active lookup")
        if (condition(lookup.itemStrings)) {
          continuation.resume(Unit)
          return@suspendCancellableCoroutine
        }

        val listener = object : LookupListener {
          override fun uiRefreshed() {
            if (condition(lookup.itemStrings)) {
              lookup.removeLookupListener(this)
              continuation.resume(Unit)
            }
          }
        }
        lookup.addLookupListener(listener)
        continuation.invokeOnCancellation { lookup.removeLookupListener(listener) }
      }
    }

    if (success == null) {
      Assertions.fail<Unit>(
        "Condition is not satisfied, actual lookup elements: ${getActiveLookup()?.itemStrings}",
        callLocationThrowable
      )
    }
  }

  suspend fun awaitPendingRequestsProcessed() {
    TerminalCommandCompletionService.getInstance(project).awaitPendingRequestsProcessed()
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

  fun insertCompletionItem(itemText: String) {
    val lookup = getActiveLookup() ?: error("No active lookup")

    val itemIndex = lookup.items.indexOfFirst { it.lookupString == itemText }
    assertThat(itemIndex)
      .overridingErrorMessage { "Item with text '$itemText' not found in lookup: ${lookup.itemStrings}" }
      .isNotEqualTo(-1)

    lookup.selectedIndex = itemIndex

    insertSelectedItem()
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
          return@suspendCancellableCoroutine
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

      // Give the output model updates settle down for a moment
      // to ensure that condition is met not in the middle of updates.
      delay(300)
      condition(view.activeOutputModel())
    }

    return result == true
  }

  /**
   * Awaits for command text state of the active block to match the [expectedCommandPattern].
   * Pattern should include `<cursor>` marker to indicate the expected cursor position.
   */
  suspend fun assertCommandTextState(expectedCommandPattern: String) {
    val cursorMarker = "<cursor>"
    val expectedText = expectedCommandPattern.replace(cursorMarker, "")
    val expectedCursorOffset = expectedCommandPattern.indexOf(cursorMarker).toLong()

    val blockModel = view.shellIntegrationDeferred.getNow()!!.blocksModel
    val activeBlock = blockModel.activeBlock as TerminalCommandBlock
    val conditionMet = awaitOutputModelState(3.seconds) { outputModel ->
      val commandStartOffset = activeBlock.commandStartOffset ?: return@awaitOutputModelState false
      val textBeforeCursor = outputModel.getText(commandStartOffset, outputModel.cursorOffset).toString()
      val textAfterCursor = outputModel.getText(outputModel.cursorOffset, outputModel.endOffset).toString()
      val commandText = textBeforeCursor + textAfterCursor.trimEnd()

      commandText == expectedText && outputModel.cursorOffset == commandStartOffset + expectedCursorOffset
    }

    val model = outputModel
    assertThat(conditionMet)
      .overridingErrorMessage {
        val modelText = buildString {
          append(model.text)
          insert(model.cursorOffset.toAbsolute().toInt(), cursorMarker)
        }
        """
          Command text doesn't match the expected pattern: '$expectedCommandPattern'
          Current output model text:
          
        """.trimIndent() + modelText
      }
      .isTrue
  }


  fun mockTestShellCommand(testCommandSpec: ShellCommandSpec) {
    val specsProvider: ShellCommandSpecsProvider = TestCommandSpecsProvider(
      ShellCommandSpecInfo.create(testCommandSpec, ShellCommandSpecConflictStrategy.DEFAULT)
    )
    ExtensionTestUtil.maskExtensions(
      ShellCommandSpecsProvider.EP_NAME,
      listOf(specsProvider),
      coroutineScope.asDisposable()
    )
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

  companion object {
    /**
     * Creates the fixture for the [session], executes the [block] and cancels the [testScope] afterward.
     */
    suspend fun doWithCompletionFixture(
      project: Project,
      session: TerminalSession,
      testScope: CoroutineScope,
      block: suspend (TerminalCompletionFixture) -> Unit,
    ) {
      val fixture = TerminalCompletionFixture(project, session, testScope)
      try {
        block(fixture)
      }
      finally {
        testScope.cancel()
      }
    }

    val Lookup.itemStrings: List<String>
      get() = items.map { it.lookupString }
  }
}