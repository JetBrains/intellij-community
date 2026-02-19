package com.intellij.lambda.testFramework.testApi

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import kotlinx.coroutines.delay
import org.intellij.lang.annotations.Language
import java.awt.Component
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Executes the specified action with the given data context.
 * @param actionId The ID of the action to execute.
 * @param dataContext The data context for the action.
 * @param latency The optional latency to simulate during execution. Defaults to `[intellij.lambda.testFramework.testApi.defaultTestLatency]`.
 * @param waitForActionAvailabilityTimeout How long test should wait when [actionId] become available in actions list.
 *        It's a workaround for GTW-5639 Provide an API to know that Action model is initialised on Frontend
 *
 * In auto tests it is always preferable to call action via shortcuts as it is closer to what user could have done.
 *
 * See [callActionByShortcut]
 */
suspend fun executeAction(@Language("devkit-action-id") actionId: String,
                          dataContext: DataContext,
                          actionPlace: String = "",
                          latency: Duration = defaultTestLatency,
                          waitUntilActionIsEnabledTimeout: Duration = ZERO,
                          waitForActionAvailabilityTimeout: Duration = ZERO
) {
  delay(latency)


  frameworkLogger.info("Execute action: '$actionId'")
  val action = when (waitForActionAvailabilityTimeout) {
    ZERO -> serviceAsync<ActionManager>().getAction(actionId)
    else -> waitForActionExists(actionId, timeout = waitForActionAvailabilityTimeout)
  }
  requireNotNull(action) { "Couldn't find action in Action Manager by '$actionId'" }

  val updateAndPerform: suspend () -> Boolean = {
    val presentationFactory = PresentationFactory()
    Utils.expandActionGroupSuspend(DefaultActionGroup(action), presentationFactory, dataContext, actionPlace, ActionUiKind.NONE, false)
    val presentation = presentationFactory.getPresentation(action)
    if (presentation.isEnabled) {
      val event = AnActionEvent(null, dataContext, actionPlace, presentation, serviceAsync<ActionManager>(), 0)
      writeIntentReadAction {
        ActionUtil.performAction(action, event)
      }
      true
    }
    else false
  }
  when {
    updateAndPerform() -> return
    waitUntilActionIsEnabledTimeout != ZERO -> waitSuspending(
      "Waiting for action to become available", waitUntilActionIsEnabledTimeout, checker = updateAndPerform)
    else -> error("Action is not enabled $actionId")
  }
}

suspend fun waitForActionExists(@Language("devkit-action-id") actionId: String, timeout: Duration = 10.seconds): AnAction {
  return waitSuspendingNotNull("Waiting for action `$actionId` to be registered",
                                                                                        timeout) {
    ActionManagerEx.getInstanceEx().getAction(actionId)
  }
}

fun getLastExecutedActionId(): String? =
  ActionManagerEx.getInstanceEx().lastPreformedActionId //todo rewrite with the flow

suspend fun waitLastExecutedAction(actionId: String, timeout: Duration) =
  waitSuspending("Last executed action is $actionId", timeout, 10.milliseconds,
                                                                          getter = { getLastExecutedActionId() },
                                                                          checker = { it == actionId })

/**
 * Don't try to call IdeAction.ShowSettings and other system actions on MAC this way.
 * MAC Native code should be triggered for these system actions.
 * And for our custom produced key event passed directly to IdeDispatcher, that won't happen.
 *
 * See `com.apple.eawt._AppEventHandler._PreferencesDispatcher`,
 * `com.intellij.ui.mac.MacOSApplicationProviderKt.initMacApplication`
 * and `com.intellij.ide.actions.ShowSettingsAction.update` that says the presentation is disabled on MAC.
 */
context(lambdaIdeContext: LambdaIdeContext)
suspend fun callActionByShortcut(actionName: String, repeat: Int = 1, latency: Duration = defaultTestLatency) {
  val keyStroke = getKeyStroke(actionName)
  frameworkLogger.info("Going to call action '$actionName' via shortcut '$keyStroke' after $latency and $repeat time(s)")
  pressKeyStroke(keyStroke, repeat, latency)
}

/**
 * Will send key events to the specified component directly without IDE Event Queue involved, and it wouldn't require focus.
 */
context(lambdaIdeContext: LambdaIdeContext)
suspend fun Component.callActionByShortcut(actionName: String, repeat: Int = 1, latency: Duration = defaultTestLatency) {
  val keyStroke = getKeyStroke(actionName)
  frameworkLogger.info("Going to call action '$actionName' directly to component '$this'" +
                       " via shortcut '$keyStroke' after $latency and $repeat time(s)")
  pressKeyStrokesDirectly(this, keyStroke, repeat, latency)

}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callShowUsages(latency: Duration = defaultTestLatency) {
  callActionByShortcut(ShowUsagesAction.ID, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callFindUsages(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_FIND_USAGES, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callJavaDoc(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_QUICK_JAVADOC, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callQuickImplementation(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_QUICK_IMPLEMENTATIONS, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callGoToImplementation(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_GOTO_IMPLEMENTATION, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callGoToDeclaration(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_GOTO_DECLARATION, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callShowParameterInfo(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO, latency = latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callFileStructurePopup(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_FILE_STRUCTURE_POPUP, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callUndo(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_UNDO, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callRedo(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_REDO, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressWhitespace(latency: Duration = defaultTestLatency) {
  delay(latency)
  typeWithEventQueue(" ")
}

/**
 * NOTE: If you try to use this method to accept Live Template Highlighter,
 *   be aware this highlighter needs some time to be shown after calling it.
 *   Probably you need to wait for them first. Take a look at [com.intellij.lambda.testFramework.testApi.editor.waitForRangeTemplateHighlighter]
 */
context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressEnter(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_ENTER, repeat, latency)
}


context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressSmartEnter(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun deleteLine(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_DELETE_LINE, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun moveCaretToCodeBlockStart(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut("EditorCodeBlockStart", repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun moveCaretToCodeBlockEnd(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut("EditorCodeBlockEnd", repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun Component.pressEnter(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_ENTER, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressTab(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_TAB, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressBackspace(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_BACKSPACE, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressDelete(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_DELETE, repeat, latency)
}
context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressEscape(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_ESCAPE, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressUpArrow(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressDownArrow(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressLeftArrow(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressRightArrow(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun nextTemplateVariable(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE, latency = latency)
}