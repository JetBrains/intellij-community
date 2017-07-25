package training.learn

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EdtInvocationManager
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import training.commands.Command
import training.commands.CommandFactory
import training.commands.ExecutionList
import training.editor.actions.HideProjectTreeAction
import training.util.PerformActionUtil
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue


/**
 * Created by karashevich on 30/01/15.
 */
object LessonProcessor {

  val LOG = Logger.getInstance(this.javaClass.canonicalName)

  @get:TestOnly
  var currentExecutionList: ExecutionList? = null
    private set

  fun process(project: Project, lesson: Lesson, editor: Editor, target: String?) {

    val scenario = lesson.scn ?: throw Exception("Scenario is empty or cannot be read!")
    val root = scenario.root ?: throw Exception("Scenario is empty or cannot be read!")
    val myEditorParameters = getEditorParameters(root)
    val myQueueOfElements = createQueueOfCommands(root)

    //Initialize lesson in the editor
    LessonManager.getInstance(lesson)?.initLesson(editor) ?: throw Exception("Unable to get LessonManager")

    //Prepare environment before execution
    prepareEnvironment(editor, project, myEditorParameters)

    currentExecutionList = ExecutionList(myQueueOfElements, lesson, project, editor, target)
    startCommandsPipeline(myQueueOfElements)
  }

  private fun createQueueOfCommands(root: Element): BlockingQueue<Element> {
    val commandsQueue = LinkedBlockingQueue<Element>()
    for (rootChild in root.children) {
      //if element is MouseBlocked (blocks all mouse events) than add all children inside it.
      if (isMouseBlock(rootChild)) {
        if (rootChild.children != null) {
          commandsQueue.add(rootChild) //add block element
          for (mouseBlockChild in rootChild.children) {
            if (isCaretBlock(mouseBlockChild)) {
              if (mouseBlockChild.children != null) {
                commandsQueue.add(mouseBlockChild) //add block element
                commandsQueue += mouseBlockChild.children
                commandsQueue += Element(Command.CommandType.CARETUNBLOCK.toString()) //add unblock element
              }
            }
            else {
              commandsQueue.add(mouseBlockChild) //add inner elements
            }
          }
          commandsQueue.add(Element(Command.CommandType.MOUSEUNBLOCK.toString())) //add unblock element
        }
      }
      else if (isCaretBlock(rootChild)) {
        if (rootChild.children != null) {
          commandsQueue.add(rootChild) //add block element
          commandsQueue += rootChild.children
          commandsQueue += Element(Command.CommandType.CARETUNBLOCK.toString()) //add unblock element
        }
      }
      else {
        commandsQueue.add(rootChild)
      }
    }
    return commandsQueue
  }


  private fun startCommandsPipeline(elements: Queue<Element>) {
    val cmd = CommandFactory.buildCommand(elements.peek())
    //Do not invoke pipeline of commands from Edt!
    if (!EdtInvocationManager.getInstance().isEventDispatchThread)
      cmd.execute(currentExecutionList!!)
    else
      ApplicationManager.getApplication().executeOnPooledThread { cmd.execute(currentExecutionList!!) }
  }


  private fun getEditorParameters(root: Element): HashMap<String, String> {
    val editorParameters = HashMap<String, String>()
    if (root.getAttribute(Lesson.EditorParameters.PROJECT_TREE) != null) {
      editorParameters.put(Lesson.EditorParameters.PROJECT_TREE, root.getAttributeValue(Lesson.EditorParameters.PROJECT_TREE))
    }
    return editorParameters
  }

  private fun prepareEnvironment(editor: Editor, project: Project, editorParameters: HashMap<String, String>) {
    if (editorParameters.containsKey(Lesson.EditorParameters.PROJECT_TREE)) {
      if (ActionManager.getInstance().getAction(HideProjectTreeAction.myActionId) == null) {
        val hideAction = HideProjectTreeAction()
        ActionManager.getInstance().registerAction(hideAction.actionId, hideAction)
      }
      PerformActionUtil.performAction(HideProjectTreeAction.myActionId, editor, project)
    }
  }

  private fun isMouseBlock(el: Element): Boolean {
    return el.name.toUpperCase() == Command.CommandType.MOUSEBLOCK.toString()
  }

  private fun isCaretBlock(el: Element): Boolean {
    return el.name.toUpperCase() == Command.CommandType.CARETBLOCK.toString()
  }

}
