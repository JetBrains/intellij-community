package training.commands

import com.intellij.openapi.application.ApplicationManager

/**
 * Created by karashevich on 30/01/15.
 */
class ShowLineNumberCommand : Command(Command.CommandType.SHOWLINENUMBER) {

  override fun execute(executionList: ExecutionList) {
    //Block caret and perform next command
    //        ActionManager.getInstance().getAction()
    val editor = executionList.editor
    ApplicationManager.getApplication().invokeAndWait {
      editor.settings.isLineNumbersShown = true
    }
    executionList.elements.poll()
    startNextCommand(executionList)
  }
}
