package training.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import java.awt.Point
import kotlin.concurrent.thread

class LearnBalloonBuilder(private val myEditor: Editor, private val myDelay: Int, private val myText: String) {
  private val myProject: Project = myEditor.project!!
  private var lastOffset = -1
  private var reuseLastBalloon = false
  private var lastBalloon: Balloon? = null

  fun showBalloon() {

    val offset = myEditor.caretModel.currentCaret.offset
    if (lastOffset == offset && lastBalloon != null && !lastBalloon!!.isDisposed) {
      reuseLastBalloon = true
    } else {
      lastOffset = offset
      val myBalloon = createBalloon()
      lastBalloon = myBalloon
      myBalloon.setAnimationEnabled(false)
      val busConnection = myProject.messageBus.connect(myProject)
      busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          myBalloon.hide()
          myBalloon.dispose()
        }
      })
      myBalloon.show(RelativePoint(myEditor.contentComponent, pointToShowBalloon(offset)), Balloon.Position.above)
      thread {
        try {
          do {
            reuseLastBalloon = false
            Thread.sleep(myDelay.toLong())
          } while (reuseLastBalloon)
          if (!myBalloon.isDisposed) {
            myBalloon.hide()
            myBalloon.dispose()
          }
        } catch (e: InterruptedException) {
          e.printStackTrace()
        }
      }
    }
  }

  private fun pointToShowBalloon(offset: Int): Point {
    val position = myEditor.offsetToVisualPosition(offset)
    val point = myEditor.visualPositionToXY(position)
    return point
  }

  private fun createBalloon(): Balloon =
          JBPopupFactory.getInstance()
                  .createHtmlTextBalloonBuilder(myText, null, UIUtil.getLabelBackground(), null)
                  .setHideOnClickOutside(false)
                  .setCloseButtonEnabled(true)
                  .setHideOnKeyOutside(false)
                  .setAnimationCycle(0)
                  .createBalloon()
}