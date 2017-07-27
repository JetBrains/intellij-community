package training.commands

import training.learn.LessonManager
import training.ui.Message

/**
 * Created by karashevich on 30/01/15.
 */
class TextCommand : Command(Command.CommandType.TEXT) {

  @Throws(InterruptedException::class)
  override fun execute(executionList: ExecutionList) {


    val element = executionList.elements.poll()
    val lesson = executionList.lesson

    LessonManager.getInstance(lesson)?.addMessages(Message.convert(element))

    startNextCommand(executionList)

  }

}
