package circlet.tools

import circlet.settings.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.ex.*

class CircletToolWindowManager(project: Project) :
    AbstractProjectComponent(project), LifetimedComponent by SimpleLifetimedComponent() {

    override fun initComponent() {
        myProject.toolWindowManagerEx.addToolWindowManagerListener(object : ToolWindowManagerAdapter() {
            override fun toolWindowRegistered(id: String) {
                if (id == CircletToolWindowFactory.TOOL_WINDOW_ID) {

                }
            }
        })
    }


    private fun updateToolWindow(state: CircletServerSettings) {
        myProject.toolWindowManager.getToolWindow(CircletToolWindowFactory.TOOL_WINDOW_ID)
            ?.setAvailable(true, null)
    }
}
