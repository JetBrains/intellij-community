package training.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Created by jetbrains on 17/03/16.
 */
class LearnToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

    myLearnToolWindow = LearnToolWindow()
    myLearnToolWindow!!.init(project)
    val contentManager = toolWindow.contentManager

    val content = contentManager.factory.createContent(myLearnToolWindow, null, false)
    contentManager.addContent(content)

    Disposer.register(project, myLearnToolWindow!!)
  }

  companion object {
    val LEARN_TOOL_WINDOW = "Learn"
    var myLearnToolWindow: LearnToolWindow? = null
      private set
  }
}

