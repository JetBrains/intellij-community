package circlet.actions

import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*

class DisableAction : AnAction() {

    override fun update(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return

        val enabled = component<CircletLoginComponent>().enabled.value

        e.presentation.isEnabled = enabled
        e.presentation.isVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return

        project.component<CircletConnectionComponent>().disable()
    }
}

