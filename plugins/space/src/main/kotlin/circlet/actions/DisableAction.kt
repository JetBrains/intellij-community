package circlet.actions

import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*

class DisableAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && component<CircletLoginComponent>().enabled.value
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.component<CircletConnectionComponent>()?.disable()
    }
}
