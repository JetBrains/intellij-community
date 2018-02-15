package circlet.actions

import circlet.components.*
import com.intellij.openapi.actionSystem.*

class ReEnterCredentialsAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.connection?.loginModel != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.connection?.authenticate()
    }
}
