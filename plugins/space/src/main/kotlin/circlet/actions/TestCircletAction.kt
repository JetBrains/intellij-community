package circlet.actions

import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*
import klogging.*
import runtime.async.*

private val log = KLoggers.logger("plugin/TestCircletAction.kt")

class TestCircletAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.project ?: return

        val enabled = component<CircletLoginComponent>().enabled.value

        e.presentation.isEnabled = enabled
        e.presentation.isVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
    }
}
