package training.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import training.learn.exceptons.EditorNoListenersException
import training.learn.exceptons.NullEditorException

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener

/**
 * Created by karashevich on 24/02/15.
 */
class MouseListenerHolder(private val myEditor: Editor?) {

  private var myMouseListeners: Array<MouseListener>? = null
  private var myMouseMotionListeners: Array<MouseMotionListener>? = null
  private var myMouseDummyListener: MouseListener? = null
  private var mouseBlocked = false

  private val LOG = Logger.getInstance(this.javaClass.canonicalName)

  fun grabMouseActions(runInsteadMouseAction: Runnable) {
    try {
      grabMouseActionsInner(runInsteadMouseAction)
    }
    catch (e: Exception) {
      when (e) {
        is NullEditorException -> {
          LOG.warn(e)
        }
        is EditorNoListenersException -> {
          LOG.warn(e)
        }
        else -> throw e
      }
    }
  }

  private fun grabMouseActionsInner(runInsteadMouseAction: Runnable) {
    val editorContent = myEditor?.contentComponent ?: throw NullEditorException("Unable to get current editor (it is null)")

    // let's store and remove all mouse listeners and mouse motion listeners
    myMouseListeners = editorContent.mouseListeners ?: throw EditorNoListenersException("Editor doesn't contain any mouse listener")
    myMouseListeners!!.forEach { editorContent.removeMouseListener(it) }

    myMouseMotionListeners = editorContent.mouseMotionListeners ?: throw EditorNoListenersException(
      "Editor doesn't contain any mouse motion listener")
    myMouseMotionListeners!!.forEach { editorContent.removeMouseMotionListener(it) }

    myMouseDummyListener = createDummyMouseAdapter(runInsteadMouseAction)

    editorContent.addMouseListener(myMouseDummyListener)
    mouseBlocked = true
  }

  private fun createDummyMouseAdapter(runInsteadMouseAction: Runnable): MouseAdapter =
    object : MouseAdapter() {
      override fun mouseClicked(mouseEvent: MouseEvent?) = runInsteadMouseAction.run()
      override fun mousePressed(mouseEvent: MouseEvent?) = runInsteadMouseAction.run()
      override fun mouseReleased(mouseEvent: MouseEvent?) = runInsteadMouseAction.run()
    }

  fun restoreMouseActions(editor: Editor) {

    if (!mouseBlocked || myEditor == null || editor != myEditor) return  //do not restore mouse actions for disposed editors

    val editorContent = editor.contentComponent

    myMouseListeners?.forEach { editorContent.addMouseListener(it) }
    myMouseMotionListeners?.forEach { editorContent.addMouseMotionListener(it) }
    if (myMouseDummyListener != null) editorContent.removeMouseListener(myMouseDummyListener)

    mouseBlocked = false
  }

}
