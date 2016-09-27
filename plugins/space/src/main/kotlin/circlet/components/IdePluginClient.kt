package circlet.components

import circlet.client.*
import circlet.protocol.*
import rx.*
import rx.subjects.*
import javax.websocket.*

class IdePluginClient {

    private var session: Session? = null
    var connection : ModelConnection? = null

    fun connect() {
        Thread.currentThread().setContextClassLoader(this.javaClass.getClassLoader())

        session = connectToServer("ws://localhost:8084/socket-api") { cnctn, lifetime ->
            connection = cnctn
            lifetime.add {
                connection = null
            }
        }

    }

    fun disconnect(){
        session?.close()
    }
}
