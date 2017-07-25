package training.commands

import com.intellij.openapi.application.ApplicationManager
import org.jdom.Element
import training.learn.Lesson
import training.learn.LessonManager
import training.ui.Message
import training.util.XmlUtil

/**
 * Created by karashevich on 30/01/15.
 */
abstract class Command(val commandType: Command.CommandType) {

  enum class CommandType {
    START, TEXT, TRY, TRYBLOCK, ACTION, REPLAY, NOCOMMAND, MOVECARET, TYPETEXT, COPYTEXT, TRAVERSECARET, MOUSEBLOCK, MOUSEUNBLOCK, WAIT, CARETBLOCK, CARETUNBLOCK, SHOWLINENUMBER, EXPANDALLBLOCKS, WIN, TEST, SETSELECTION
  }

  internal fun updateDescription(s: String, lesson: Lesson) {
    LessonManager.getInstance(lesson)?.addMessage(s)
  }

  internal fun updateHTMLDescription(htmlText: String, lesson: Lesson) {
    val messages = XmlUtil.extractAll(arrayOf(Message(htmlText, Message.MessageType.TEXT_REGULAR)))
    val lessonManager = LessonManager.getInstance(lesson)
    lessonManager?.addMessage(messages)
  }

  /**

   * @return true if button is updated
   */
  //updateButton(element, elements, lesson, editor, e, document, target, infoPanel);
  @Throws(InterruptedException::class)
  protected fun updateButton(executionList: ExecutionList): Boolean {
    return true
  }

  protected fun initAgainButton() {}

  abstract fun execute(executionList: ExecutionList)

  protected fun startNextCommand(executionList: ExecutionList) {
    CommandFactory.buildCommand(executionList.elements.peek()).execute(executionList)
  }

  protected fun executeWithPoll(executionList: ExecutionList, function: (Element) -> Unit) {
    val element = executionList.elements.poll()
    function(element)
    startNextCommand(executionList)
  }

  protected fun executeWithPollOnEdt(executionList: ExecutionList, function: (Element) -> Unit) {
    val element = executionList.elements.poll()
    ApplicationManager.getApplication().invokeAndWait {
      function(element)
    }
    startNextCommand(executionList)
  }


}
