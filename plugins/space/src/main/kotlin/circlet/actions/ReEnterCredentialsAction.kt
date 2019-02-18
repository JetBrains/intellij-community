package circlet.actions

import com.intellij.openapi.actionSystem.*

class ReEnterCredentialsAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {

    }
}
