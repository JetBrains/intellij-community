package circlet.actions

import circlet.components.*
import circlet.model.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*
import klogging.*
import rx.subjects.*

private val log = KLoggers.logger("plugin/TestCircletAction.kt")

class TestCircletAction : AnAction() {

    override fun update(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return

        e.presentation.isEnabled = project.component<IdePluginClient>().connectionState != null
    }

    override fun actionPerformed(e: AnActionEvent?) {
        e ?: return
        val project = e.project
        project ?: return
        val client = project.component<IdePluginClient>()
        val connection = client.connectionState?.connection ?: return

        ServerApi(connection).spaces() then {
            it.forEach {
                log.info { "Space: ${it.name}" }
            }
        } catch {
            // >>> wtf...
        }

    }
}
