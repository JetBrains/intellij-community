package training.commands

import training.check.Check
import training.keymap.KeymapUtil
import training.learn.ActionsRecorder
import training.learn.Lesson
import training.learn.LessonManager
import training.ui.Message
import training.util.MyClassLoader
import java.io.IOException
import java.util.*

/**
 * Created by karashevich on 30/01/15.
 */
class TryCommand : Command(Command.CommandType.TRY) {

  @Throws(Exception::class)
  override fun execute(executionList: ExecutionList) {

    val element = executionList.elements.poll()
    var check: Check? = null
    //        updateDescription(element, infoPanel, editor);

    var target: String? = executionList.target
    val lesson = executionList.lesson
    val editor = executionList.editor

    if (element.getAttribute("target") != null)
      try {
        target = getFromTarget(lesson, element.getAttribute("target")!!.value)
      }
      catch (e1: IOException) {
        e1.printStackTrace()
      }

    LessonManager.getInstance(lesson)?.addMessages(Message.convert(element))

    //Show button "again"
    //        updateButton(element, elements, lesson, editor, e, document, myTarget, infoPanel, mouseListenerHolder);

    val recorder = ActionsRecorder(editor.project!!, editor.document, target, editor)
    LessonManager.getInstance(lesson).registerActionsRecorder(recorder)

    if (element.getAttribute("check") != null) {
      val checkClassString = element.getAttribute("check")!!.value
      try {
        val myCheck = Class.forName(checkClassString)
        check = myCheck.newInstance() as Check
        check.set(executionList.project, editor)
        check.before()
      }
      catch (e: Exception) {
        e.printStackTrace()
      }

    }
    if (element.getAttribute("trigger") != null) {
      val actionId = element.getAttribute("trigger")!!.value
      startRecord(executionList, recorder, actionId, check)
    }
    else if (element.getAttribute("triggers") != null) {
      val actionIds = element.getAttribute("triggers")!!.value
      val actionIdArray = actionIds.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      startRecord(executionList, recorder, actionIdArray, check)
    }
    else {
      startRecord(executionList, recorder, check)
    }

  }

  @Throws(Exception::class)
  private fun startRecord(executionList: ExecutionList, recorder: ActionsRecorder, check: Check?) {
    recorder.startRecording(getDoWhenDone(executionList), null as Array<String>?, check)
  }

  @Throws(Exception::class)
  private fun startRecord(executionList: ExecutionList, recorder: ActionsRecorder, actionId: String, check: Check?) {
    recorder.startRecording(getDoWhenDone(executionList), actionId, check)
  }

  @Throws(Exception::class)
  private fun startRecord(executionList: ExecutionList, recorder: ActionsRecorder, actionIdArray: Array<String>, check: Check?) {
    recorder.startRecording(getDoWhenDone(executionList), actionIdArray, check)

  }

  private fun getDoWhenDone(executionList: ExecutionList): Runnable
    = Runnable { pass(executionList) }

  private fun pass(executionList: ExecutionList) {
    val lesson = executionList.lesson
    LessonManager.getInstance(lesson).passExercise()
    val lessonLog = lesson.lessonLog
    lessonLog.log("Passed exercise. Exercise #" + lessonLog.getMyLesson().exerciseCount)
    lesson.passItem()
    startNextCommand(executionList)
  }


  @Throws(IOException::class)
  private fun getFromTarget(lesson: Lesson, targetPath: String): String {
    val `is` = MyClassLoader.getInstance().getResourceAsStream(lesson.module!!.answersPath!! + targetPath) ?: throw IOException(
      "Unable to get checkfile for \"" + lesson.name + "\" lesson")
    return Scanner(`is`).useDelimiter("\\Z").next()
  }

  private fun resolveShortcut(text: String, actionId: String): String {
    val shortcutByActionId = KeymapUtil.getShortcutByActionId(actionId)
    val shortcutText = KeymapUtil.getKeyStrokeText(shortcutByActionId)
    return substitution(text, shortcutText)
  }

  companion object {

    fun substitution(text: String, shortcutString: String): String {
      if (text.contains(ActionCommand.SHORTCUT)) {
        return text.replace(ActionCommand.SHORTCUT, shortcutString)
      }
      else {
        return text
      }
    }
  }

}
