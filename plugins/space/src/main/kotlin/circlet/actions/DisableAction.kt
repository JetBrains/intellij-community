package circlet.actions

import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*

class DisableAction : AnAction() {

    override fun update(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return

        e.presentation.isEnabled = project.component<IdePluginClient>().enabled.value
    }

    override fun actionPerformed(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return

        project.component<IdePluginClient>().disable()
    }
}
