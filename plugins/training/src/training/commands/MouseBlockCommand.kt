package training.commands

import training.learn.LessonManager

/**
 * Created by karashevich on 30/01/15.
 */
class MouseBlockCommand : Command(Command.CommandType.MOUSEBLOCK) {

  override fun execute(executionList: ExecutionList) {
    //Block mouse and perform next
    LessonManager.getInstance(executionList.lesson)?.blockMouse(executionList.editor)
    executionList.elements.poll()
    startNextCommand(executionList)

  }
}
