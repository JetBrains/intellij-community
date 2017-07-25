package training.learn

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import training.check.Check
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.*

/**
 * Created by karashevich on 18/12/14.
 */
class ActionsRecorder(private val project: Project,
                      private val document: Document,
                      private val target: String?,
                      private val editor: Editor) : Disposable {
  private var triggerActivated: Boolean = false
  private var triggerQueue: Queue<String>? = null

  private var myDocumentListener: DocumentListener? = null
  private var myAnActionListener: AnActionListener? = null
  private var myKeyListener: KeyListener? = null

  private var disposed = false
  private var doWhenDone: Runnable? = null
  private var check: Check? = null

  init {
    this.doWhenDone = null

    Disposer.register(project, this)
  }

  override fun dispose() {
    removeListeners(document, ActionManager.getInstance())
    disposed = true
    Disposer.dispose(this)
  }

  fun startRecording(doWhenDone: Runnable) {
    if (disposed) return
    this.doWhenDone = doWhenDone

  }

  fun startRecording(doWhenDone: Runnable, actionId: String?, check: Check?) {
    val stringArray = arrayOf<String>(actionId!!)
    startRecording(doWhenDone, stringArray, check)
  }


  fun startRecording(doWhenDone: Runnable, actionIdArray: Array<String>?, check: Check?) {
    if (check != null) this.check = check
    if (disposed) return
    this.doWhenDone = doWhenDone

    //        triggerMap = new HashMap<String, Boolean>(actionIdArray.length);
    triggerQueue = LinkedList<String>()
    //set triggerMap
    if (actionIdArray != null) {
      Collections.addAll(triggerQueue!!, *actionIdArray)
    }
    addActionAndDocumentListeners()
  }


  private fun isTaskSolved(current: Document, target: String?): Boolean {
    if (disposed) return false

    if (target == null) {
      if (triggerQueue != null) {
        return (triggerQueue!!.size == 1 || triggerQueue!!.size == 0) && (check == null || check!!.check())
      }
      else
        return triggerActivated && (check == null || check!!.check())
    }
    else {

      val expected = computeTrimmedLines(target)
      val actual = computeTrimmedLines(current.text)

      if (triggerQueue != null) {
        return expected == actual && triggerQueue!!.size == 0 && (check == null || check!!.check())
      }
      else
        return expected == actual && triggerActivated && (check == null || check!!.check())
    }

  }

  private fun computeTrimmedLines(s: String): List<String> {
    val ls = ArrayList<String>()

    for (it in StringUtil.splitByLines(s)) {
      val splitted = it.split("[ ]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      for (element in splitted)
        if (element != "") {
          ls.add(element)
        }
    }
    return ls
  }


  /**
   * method adds action and document listeners to monitor user activity and check task
   */
  private fun addActionAndDocumentListeners() {
    val actionManager = ActionManager.getInstance() ?: throw Exception("Unable to get instance for ActionManager")

    myAnActionListener = object : AnActionListener {

      private var editorFlag: Boolean = false

      override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        editorFlag = event.project != null && FileEditorManager.getInstance(event.project!!).selectedTextEditor === editor
      }

      override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {

        //if action called not from project or current editor is different from editor
        if (!editorFlag) return

        if (triggerQueue!!.size == 0) {
          if (isTaskSolved(document, target)) {
            //                        actionManager.removeAnActionListener(this);
            removeListeners(document, actionManager)
            if (doWhenDone != null) {
              dispose()
              doWhenDone!!.run()
            }
          }
        }
        if (checkAction(action, triggerQueue!!.peek())) {
          if (triggerQueue!!.size > 1) {
            triggerQueue!!.poll()
          }
          else if (triggerQueue!!.size == 1) {
            if (isTaskSolved(document, target)) {
              //                            actionManager.removeAnActionListener(this);
              removeListeners(document, actionManager)
              if (doWhenDone != null) {
                dispose()
                doWhenDone!!.run()
              }
            }
            else {
              triggerQueue!!.poll()
            }
          }
        }
      }

      override fun beforeEditorTyping(c: Char, dataContext: DataContext) {}
    }


    myDocumentListener = object : DocumentListener {


      override fun beforeDocumentChange(event: DocumentEvent) {}

      override fun documentChanged(event: DocumentEvent) {
        if (PsiDocumentManager.getInstance(project).isUncommited(document)) {
          ApplicationManager.getApplication().invokeLater {

            if (!disposed && !project.isDisposed) {
              PsiDocumentManager.getInstance(project).commitAndRunReadAction { doWhenTriggerIsEmptyAndTaskSolved(actionManager) }
            }
          }
        }
      }
    }


    val myEventDispatcher = IdeEventQueue.EventDispatcher { e ->
      if (e is KeyEvent) {
        doWhenTriggerIsEmptyAndTaskSolved(actionManager)
      }
      false
    }


    if (check != null && check!!.listenAllKeys()) IdeEventQueue.getInstance().addDispatcher(myEventDispatcher, this)
    document.addDocumentListener(myDocumentListener!!)
    actionManager.addAnActionListener(myAnActionListener)
  }

  private fun doWhenTriggerIsEmptyAndTaskSolved(actionManager: ActionManager) {
    if (triggerQueue!!.isEmpty()) {
      if (isTaskSolved(document, target)) {
        removeListeners(document, actionManager)
        if (doWhenDone != null)
          dispose()
        assert(doWhenDone != null)
        doWhenDone!!.run()
      }
    }
  }

  /**
   * @param action - caught action by AnActionListener (see [.addActionAndDocumentListeners] addActionAndDocumentListeners()} method.)
   * @param actionString - could be an actionId or action class name
   * @return if action equals to actionId or have the same action class name
   */
  private fun checkAction(action: AnAction, actionString: String?): Boolean {
    val actionId = ActionManager.getInstance().getId(action)
    val actionClassName = action.javaClass.name
    return if (actionId != null) equalStr(actionId, actionString) || equalStr(actionClassName, actionString)
    else equalStr(actionClassName, actionString)
  }

  private fun removeListeners(document: Document, actionManager: ActionManager) {
    if (myAnActionListener != null) actionManager.removeAnActionListener(myAnActionListener)
    if (myDocumentListener != null) document.removeDocumentListener(myDocumentListener!!)
    if (myKeyListener != null) editor.component.rootPane.glassPane.removeKeyListener(myKeyListener)
    myKeyListener = null
    myAnActionListener = null
    myDocumentListener = null
  }

  private fun equalStr(str1: String?, str2: String?): Boolean {
    return !(str1 == null || str2 == null) && str1.toUpperCase() == str2.toUpperCase()
  }
}

