package circlet.tools

import circlet.settings.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*
import runtime.reactive.*

class CircletToolWindowsManager(project: Project) :
    AbstractProjectComponent(project), Lifetimed by LifetimedOnDisposable(project) {

    override fun initComponent() {
        myProject.settings.stateProperty.forEach(lifetime) { updateToolWindows(it) }

        ToolWindowManagerEx.getInstanceEx(myProject).addToolWindowManagerListener(object : ToolWindowManagerAdapter() {
            override fun toolWindowRegistered(id: String) {
                if (id in TOOL_WINDOW_IDS) {
                    updateToolWindows(arrayOf(id))
                }
            }
        })

        updateToolWindows()
    }

    private fun updateToolWindows(ids: Array<String> = TOOL_WINDOW_IDS) {
        updateToolWindows(myProject.settings.stateProperty.value, ids)
    }

    private fun updateToolWindows(state: CircletProjectSettings.State, ids: Array<String> = TOOL_WINDOW_IDS) {
        val available = state.isIntegrationAvailable
        val toolWindowManager = ToolWindowManager.getInstance(myProject)

        ids.forEach { toolWindowManager.getToolWindow(it)?.setAvailable(available, null) }
    }

    private companion object {
        private val TOOL_WINDOW_IDS = arrayOf(CircletReviewsToolWindowFactory.TOOL_WINDOW_ID)
    }
}
