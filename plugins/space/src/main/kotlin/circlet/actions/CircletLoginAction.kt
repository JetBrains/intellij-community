package circlet.actions

import circlet.components.*
import circlet.settings.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.*

class CircletLoginAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = circletWorkspace.workspace.value == null
        CircletActionUtils.showIconInActionSearch(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        CircletSettingsPanel.openSettings(e.project)
    }
}
