package circlet.actions

import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.actionSystem.*

class ConnectAction : AnAction() {

    override fun update(e: AnActionEvent?) {
        e ?: return
        e.presentation.isEnabled = component<IdePluginClient>().connection == null
    }

    override fun actionPerformed(e: AnActionEvent?) =component<IdePluginClient>().connect()
}

class DisconnectAction : AnAction() {

    override fun update(e: AnActionEvent?) {
        e ?: return
        e.presentation.isEnabled = component<IdePluginClient>().connection != null
    }

    override fun actionPerformed(e: AnActionEvent?) =component<IdePluginClient>().disconnect()
}
