package circlet.actions

import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*

class ReEnterCredentials : AnAction() {

    override fun update(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return
        val component = project.component<CircletLoginComponent>()

        e.presentation.isEnabled = component.enabled.value
        e.presentation.isVisible = component.enabled.value
    }

    override fun actionPerformed(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return

        project.component<CircletConnectionComponent>().authenticate()
    }
}
