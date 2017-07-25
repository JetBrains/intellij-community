package training.commands


import training.learn.LessonManager

/**
 * Created by karashevich on 30/01/15.
 */
class MouseUnblockCommand : Command(Command.CommandType.MOUSEUNBLOCK) {

  override fun execute(executionList: ExecutionList) {
    executionList.elements.poll()
    LessonManager.getInstance(executionList.lesson).unblockMouse(executionList.editor)
    startNextCommand(executionList)
  }
}
