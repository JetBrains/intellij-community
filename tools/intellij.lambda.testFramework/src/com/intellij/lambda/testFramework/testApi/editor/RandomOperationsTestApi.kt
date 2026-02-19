package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.remoteDev.tests.LambdaIdeContext
import kotlinx.coroutines.yield
import kotlin.random.Random


context(lambdaIdeContext: LambdaIdeContext)
suspend fun performRandomOperationInCurrentEditor() {
  withCurrentFile(waitForReadyState = false, requireFocus = false) {
    performRandomOperation()
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.performRandomOperation() {
  val op = if (document.textLength == 0) {
    TypeText()
  }
  else {
    TestEditorOperation.nextRandomOperation(this)
  }

  frameworkLogger.info("Performing $op")
  op.performOperation(this)
}

abstract class TestEditorOperation {
  companion object {
    val random by lazy {
      val seed = Random.nextLong() //42
      frameworkLogger.info("Random seed: $seed")
      Random(seed)
    }

    private val operationFactories = listOf<(EditorImpl) -> TestEditorOperation>(
      { TypeText() },
      { SelectText(it.document.textLength) },
      { MoveCaretToOffset(it.document.textLength) },
      { CallAction() }
    )

    fun nextRandomOperation(editor: EditorImpl): TestEditorOperation {
      val factory = operationFactories[random.nextInt(0, operationFactories.size)]
      return factory.invoke(editor)
    }
  }

  abstract suspend fun performOperation(editor: EditorImpl)

  fun generateDocOffset(docLength: Int): Int {
    return random.nextInt(0, docLength)
  }
}

class TypeText : TestEditorOperation() {
  val text: String

  init {
    val typeLetter = random.nextBoolean()
    text = if (typeLetter) {
      val length = random.nextInt(1, 10)
      val sb = StringBuilder()
      repeat(length) { sb.append(random.nextInt('a'.code, 'z'.code).toChar()) }
      sb.toString()
    }
    else {
      val length = random.nextInt(1, 2)
      val chars = """{}[]()<>;$"'.!"""
      val sb = StringBuilder()
      repeat(length) { sb.append(chars[random.nextInt(0, chars.length)]) }
      sb.toString()
    }
  }

  override suspend fun performOperation(editor: EditorImpl) {
    for (ch in text) {
      writeIntentReadAction { editor.type(ch.toString()) }
      yield()
    }
  }

  override fun toString(): String {
    return "Typing '$text'"
  }
}

class SelectText(maxLength: Int) : TestEditorOperation() {
  val start = generateDocOffset(maxLength)
  val end = generateDocOffset(maxLength)
  override suspend fun performOperation(editor: EditorImpl) {
    writeIntentReadAction {
      editor.selectionModel.setSelection(start, end)
    }
  }

  override fun toString(): String {
    return "Selecting range [$start, $end]"
  }
}

class MoveCaretToOffset(maxLength: Int) : TestEditorOperation() {
  val offset = generateDocOffset(maxLength)
  override suspend fun performOperation(editor: EditorImpl) {
    writeIntentReadAction {
      editor.caretModel.moveToOffset(offset)
    }
  }

  override fun toString(): String {
    return "Moving caret to $offset"
  }
}

class CallAction : TestEditorOperation() {
  companion object {
    private val typedActionIds = listOf(
      IdeActions.ACTION_EDITOR_BACKSPACE,
      IdeActions.ACTION_EDITOR_DELETE,
      IdeActions.ACTION_EDITOR_ENTER,
      // TODO: these actions lead to test failures
      //IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET,
      //IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET,
      //IdeActions.ACTION_UNDO,
      //IdeActions.ACTION_REDO,
      IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT,
      IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT,
      IdeActions.ACTION_EDITOR_MOVE_CARET_UP,
      IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN,
      IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION,
      IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION,
      IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION,
      IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION,
      IdeActions.ACTION_EDITOR_TAB,
      IdeActions.ACTION_EDITOR_INDENT_SELECTION,
      IdeActions.ACTION_EDITOR_UNINDENT_SELECTION,
    )
  }

  val actionId = typedActionIds[random.nextInt(0, typedActionIds.size)]

  override suspend fun performOperation(editor: EditorImpl) {
    callAction(actionId, editor)
  }

  private suspend fun callAction(actionId: String, editor: EditorImpl) {
    val action = ActionManager.getInstance().getAction(actionId) ?: error("Not found '$actionId' action")

    val dataContext = editor.dataContext
    val presentation = Presentation()
    val actionEvent = AnActionEvent.createEvent(
      dataContext, presentation, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)

    ActionUtil.performDumbAwareUpdate(action, actionEvent, true)
    if (!presentation.isEnabled) {
      return
    }

    writeIntentReadAction {
      ActionUtil.performAction(action, actionEvent)
    }
  }

  override fun toString(): String {
    return "Calling action '$actionId'"
  }
}