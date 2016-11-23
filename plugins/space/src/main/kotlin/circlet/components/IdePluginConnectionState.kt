package circlet.components

import circlet.protocol.*
import runtime.lifetimes.*
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
