package training.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import training.keymap.KeymapUtil
import training.util.PerformActionUtil
import training.util.PerformActionUtil.performAction
import java.awt.Point
import kotlin.concurrent.thread

/**
 * Created by karashevich on 30/01/15.
 */
class ActionCommand : Command(Command.CommandType.ACTION) {


  override fun execute(executionList: ExecutionList) {

    val element = executionList.elements.poll()
    val editor = executionList.editor
    val project = executionList.project


    val actionType = element.getAttribute("action")!!.value

    if (element.getAttribute("balloon") != null) {

      val balloonText = element.getAttribute("balloon")!!.value

      ApplicationManager.getApplication().invokeLater {
        try {
          var delay = 0
          if (element.getAttribute("delay") != null) {
            delay = Integer.parseInt(element.getAttribute("delay")!!.value)
          }
          showBalloon(editor, balloonText, project, delay, actionType, Runnable { startNextCommand(executionList) })
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
      }
    }
    else {
      PerformActionUtil.performAction(actionType, editor, project) { startNextCommand(executionList) }
    }
  }

  companion object {

    val LOG = Logger.getInstance(ActionCommand::class.java.canonicalName)
    val SHORTCUT = "<shortcut>"

    private fun showBalloon(editor: Editor?, text: String, project: Project, delay: Int, actionType: String?, runnable: Runnable) {
      FileEditorManager.getInstance(project) ?: return
      editor ?: return

      val offset = editor.caretModel.currentCaret.offset
      val position = editor.offsetToVisualPosition(offset)
      val point = editor.visualPositionToXY(position)
      val balloonText = createBalloonText(text, actionType)
      val myBalloon = createBalloon(balloonText)
      showBalloonOnScreen(myBalloon, editor, point, delay)
      addBalloonListener(myBalloon, project, actionType, editor, runnable)
    }

    private fun addBalloonListener(myBalloon: Balloon,
                                   project: Project,
                                   actionType: String?,
                                   editor: Editor?,
                                   runnable: Runnable) {
      myBalloon.addListener(object : JBPopupListener {
        override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}

        override fun onClosed(lightweightWindowEvent: LightweightWindowEvent) {
          WriteCommandAction.runWriteCommandAction(project, Runnable {
            try {
              performAction(actionType, editor, project, runnable)
            } catch (e: Exception) {
              LOG.warn(e)
            }
          })
        }
      })
    }

    private fun showBalloonOnScreen(myBalloon: Balloon,
                                    editor: Editor,
                                    point: Point,
                                    delay: Int) {
      myBalloon.show(RelativePoint(editor.contentComponent, point), Balloon.Position.above)
      thread(name = "Show Balloon Thread") {
        Thread.sleep(delay.toLong())
        myBalloon.hide()
      }
    }

    private fun createBalloon(balloonText: String): Balloon {
      return JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(balloonText, null, UIUtil.getLabelBackground(), null)
        .setHideOnClickOutside(false)
        .setCloseButtonEnabled(true)
        .setHideOnKeyOutside(false)
        .createBalloon()
    }

    private fun createBalloonText(text: String, actionType: String?): String {
      var balloonText = text

      if (actionType != null) {
        val shortcutByActionId = KeymapUtil.getShortcutByActionId(actionType)
        val shortcutText = KeymapUtil.getKeyStrokeText(shortcutByActionId)
        balloonText = substitution(balloonText, shortcutText)
      }
      return balloonText
    }

    fun substitution(text: String, shortcutString: String): String =
      if (text.contains(SHORTCUT)) text.replace(SHORTCUT, shortcutString)
      else text

  }
}



