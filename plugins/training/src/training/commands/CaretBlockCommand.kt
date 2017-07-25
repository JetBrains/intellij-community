package training.commands

import training.learn.LessonManager

/**
 * Created by karashevich on 30/01/15.
 */
class CaretBlockCommand : Command(Command.CommandType.CARETBLOCK) {

  override fun execute(executionList: ExecutionList) {
    executionList.elements.poll()
    //Block caret and perform next command
    LessonManager.getInstance(executionList.lesson).blockCaret(executionList.editor)
    startNextCommand(executionList)
  }
}
