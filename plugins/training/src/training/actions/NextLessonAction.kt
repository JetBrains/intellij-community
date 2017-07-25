package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import training.ui.LearnToolWindowFactory
import training.ui.views.LearnPanel

/**
 * Skip lesson or go to the next lesson
 *
 * Shortcuts:
 * win: alt + shift + right
 * mac: ctrl + shift + right
 * (see the plugin.xml to change shortcuts)
 */
class NextLessonAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    //check if the lesson view is active
    ToolWindowManager.getInstance(e.project!!)
    val myLearnToolWindow = LearnToolWindowFactory.myLearnToolWindow ?: throw Exception("Unable to get Learn toolwindow (is null)")
    val view = myLearnToolWindow.scrollPane!!.viewport.view

    //click button to skip or go to the next lesson
    if (view is LearnPanel) view.clickButton()
  }
}
