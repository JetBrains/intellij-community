package circlet.tools

import circlet.settings.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.ex.*

class CircletToolWindowManager(project: Project) :
    AbstractProjectComponent(project), LifetimedComponent by SimpleLifetimedComponent() {

    override fun initComponent() {
        myProject.settings.stateProperty.forEach(lifetime) { updateToolWindow(it) }

        myProject.toolWindowManagerEx.addToolWindowManagerListener(object : ToolWindowManagerAdapter() {
            override fun toolWindowRegistered(id: String) {
                if (id == CircletToolWindowFactory.TOOL_WINDOW_ID) {
                    updateToolWindow()
                }
            }
        })

        updateToolWindow()
    }

    private fun updateToolWindow() {
        updateToolWindow(myProject.settings.stateProperty.value)
    }

    private fun updateToolWindow(state: ProjectSettings.State) {
        myProject.toolWindowManager.getToolWindow(CircletToolWindowFactory.TOOL_WINDOW_ID)
            ?.setAvailable(state.isIntegrationAvailable, null)
    }
}
