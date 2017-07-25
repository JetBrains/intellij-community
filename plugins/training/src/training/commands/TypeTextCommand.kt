package training.commands

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.text.CharArrayCharSequence

/**
 * Created by karashevich on 30/01/15.
 */
class TypeTextCommand : Command(Command.CommandType.TYPETEXT) {

  override fun execute(executionList: ExecutionList) {
    executeWithPollOnEdt(executionList) {
      element ->

      val textToType = if (element.content.isEmpty()) "" else element.content[0].value
      val startOffset = executionList.editor.caretModel.offset

      var isTyping = true
      val i = intArrayOf(0)

      while (isTyping) {
        val finalI = i[0]
        WriteCommandAction.runWriteCommandAction(executionList.project, Runnable {
          executionList.editor.document.insertString(finalI + startOffset, textToType.subSequence(i[0], i[0] + 1))
          executionList.editor.caretModel.moveToOffset(finalI + 1 + startOffset)
        })
        isTyping = ++i[0] < textToType.length
      }
    }
  }

  private fun typeText(executionList: ExecutionList, text: String, startOffset: Int) {

  }

  private fun typeChar(executionList: ExecutionList, char: Char, startOffset: Int, relativeOffset: Int) {
    WriteCommandAction.runWriteCommandAction(executionList.project, Runnable {
      executionList.editor.document.insertString(relativeOffset + startOffset, char.toSequence())
      executionList.editor.caretModel.moveToOffset(relativeOffset + 1 + startOffset)
    })
  }

  private fun Char.toSequence(): CharSequence {
    return CharArrayCharSequence(this)
  }
}
