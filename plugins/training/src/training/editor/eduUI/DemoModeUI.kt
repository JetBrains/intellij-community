package training.editor.eduUI

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Created by karashevich on 26/10/15.
 */
class DemoModeUI {
  private var demoModeWidget: DemoModeWidget? = null
  private var myProject: Project? = null

  fun addDemoModeWidget(project: Project) {

    myProject = project

    val frame = WindowManagerEx.getInstanceEx().getFrame(project)
    val statusBar = frame!!.statusBar

    if (statusBar != null) {
      if (statusBar.getWidget(DemoModeWidget.DEMO_MODE_WIDGET_ID) != null) {
        demoModeWidget = statusBar.getWidget(DemoModeWidget.DEMO_MODE_WIDGET_ID) as DemoModeWidget?
      }
      else {
        if (demoModeWidget == null) {
          demoModeWidget = DemoModeWidget(project)
          statusBar.addWidget(demoModeWidget!!, "before Position")
        }
        else {
          statusBar.addWidget(demoModeWidget!!, "before Position")
        }
      }
      statusBar.updateWidget(demoModeWidget!!.ID())
    }
  }

  fun updateDemoModeWidget() {
    if (myProject == null) return

    val frame = WindowManagerEx.getInstanceEx().getFrame(myProject)
    val statusBar = frame!!.statusBar

    statusBar.updateWidget(demoModeWidget!!.ID())
  }

  fun removeDemoModeWidget() {
    val frame = WindowManagerEx.getInstanceEx().getFrame(myProject)
    val statusBar = frame!!.statusBar

    statusBar.removeWidget(demoModeWidget!!.ID())
    demoModeWidget = null
  }

  companion object {

    val demoCurtainColor = JBColor(Color(42, 42, 42, 26), Color(128, 128, 128, 26))
  }
}