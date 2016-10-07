package circlet.components

import circlet.modelApi.*
import circlet.protocol.*
import circlet.utils.lifetime.*
import javax.websocket.*

data class IdePluginConnectionState (
    val def : LifetimeDefinition,
    var connection : ModelConnection? = null,
    var session: Session? = null,
    var message: String = "Connecting",
    var connectionState : IdePluginClient.ConnectionStates = IdePluginClient.ConnectionStates.Connecting) {

    fun Close() {
        def.terminate()
        session?.close()
    }
}
