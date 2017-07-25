package training.editor.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import java.util.*

/**
 * Created by karashevich on 19/08/15.
 */
class BlockCaretAction(private val editor: Editor) : DumbAwareAction(LearnActions.LEARN_BLOCK_EDITOR_CARET_ACTION), LearnActions {

  override val actionId: String
    get() = LearnActions.LEARN_BLOCK_EDITOR_CARET_ACTION

  private var actionHandlers: ArrayList<Runnable>? = null


  init {
    actionHandlers = ArrayList<Runnable>()

    //collect all shortcuts for caret actions
    val superShortcut = ArrayList<Shortcut>()
    val caretActionIds = HashSet<String>()
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
    caretActionIds.add(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION)

    //block clone caret
    caretActionIds.add(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE)
    caretActionIds.add(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW)

    //tab
    caretActionIds.add(IdeActions.ACTION_EDITOR_TAB)
    caretActionIds.add(IdeActions.ACTION_EDITOR_EMACS_TAB)

    caretActionIds
      .map { ActionManager.getInstance().getAction(it).shortcutSet.shortcuts }
      .forEach { Collections.addAll(superShortcut, *it) }

    val shortcutSet = CustomShortcutSet(*superShortcut.toTypedArray())
    this.registerCustomShortcutSet(shortcutSet, editor.component)
  }

  override fun unregisterAction() {
    this.unregisterCustomShortcutSet(editor.component)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (actionHandlers != null && actionHandlers!!.size > 0) {
      for (actionHandler in actionHandlers!!) {
        actionHandler.run()
      }
    }
  }


  fun addActionHandler(runnable: Runnable) {
    if (actionHandlers == null) actionHandlers = ArrayList<Runnable>()
    actionHandlers!!.add(runnable)
  }

  fun removeActionHandler(runnable: Runnable) {
    if (actionHandlers!!.contains(runnable)) {
      actionHandlers!!.remove(runnable)
    }
  }

  fun removeAllActionHandlers() {
    actionHandlers = null
  }
}
