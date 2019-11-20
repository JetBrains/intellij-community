package circlet.settings

import circlet.workspaces.*
import libraries.coroutines.extra.*

sealed class CircletLoginState(val server: String) {
    class Disconnected(server: String, val error: String? = null) : CircletLoginState(server)
    class Connected(server: String, val workspace: Workspace) : CircletLoginState(server)
    class Connecting(server: String, val lt: LifetimeSource) : CircletLoginState(server)
}
