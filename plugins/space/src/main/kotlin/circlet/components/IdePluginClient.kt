package circlet.components

import circlet.client.*
import circlet.client.auth.*
import circlet.protocol.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.util.net.ssl.*
import lifetime.*
import nl.komponents.kovenant.*
import org.apache.http.client.utils.*
import rx.subjects.*
import java.util.*
import javax.websocket.*

enum class ConnectionStates {
    Connecting,
    Connected
}

data class IdePluginConnectionState (
    val def : LifetimeDefinition,
    var connection : ModelConnection? = null,
    var session: Session? = null,
    var message: String = "Connecting",
    var connectionState : ConnectionStates = ConnectionStates.Connecting) {

    fun Close() {
        def.terminate()
        session?.close()
    }
}

class IdePluginClient(project : Project) : AbstractProjectComponent(project) {

    private val serverConfig = ServerConfig()
    var logger = Logger.getInstance(javaClass)
    var connectionState : IdePluginConnectionState? = null

    fun connect() {
        if (connectionState != null)
        {
            logger.error("Can't connect twice")
            return
        }

        val connectedStateLifetimeDef = Lifetime.create(Lifetime.Eternal)
        val connectingProgressLifetimeDef = Lifetime.create(connectedStateLifetimeDef.lifetime)
        val state = IdePluginConnectionState(connectedStateLifetimeDef)
        connectionState = state

        ProgressManager.getInstance().run(externalTask(myProject, true, object: IExternalTask {
            override val lifetime: Lifetime get() = connectingProgressLifetimeDef.lifetime
            override val cancel: () -> Unit get() = { disconnect() }
            override val title: String get() = connectionState?.message ?: "Connecting"
            override val header: String get() = connectionState?.message ?: "Connecting"
            override val description: String get() = connectionState?.message ?: "Connecting"
            override val isIndeterminate: Boolean get() = true
            override val progress: Double get() = 0.5
        }))

        task {
            Thread.currentThread().setContextClassLoader(this.javaClass.getClassLoader())

            RingAuth(CertificateManager.getInstance().getSslContext(), CertificateManager.HOSTNAME_VERIFIER, serverConfig, connectedStateLifetimeDef.lifetime).apply {
                connectionState?.message = "Connecting to hub"
                hubInfo() thenAwait {
                    logger.info("got hub info $it")
                    connectionState?.message = "Getting auth code"
                    authCode(it)
                } thenAwait {
                    logger.info("got auth code")
                    connectionState?.message = "Getting refresh token"
                    refreshToken(it)
                } thenAwait {
                    logger.info("got refresh token")
                    connectionState?.message = "Getting access token"
                    authToken(it)
                } then {
                    try {
                        val me = user(it)

                        connectionState?.message = "Connecting to circlet server"
                        val token = it.accessTokenSource.accessToken
                        logger.info("got access token: $token")
                        var builder = URIBuilder(serverConfig.serviceWebSocketUrl).
                            setParameter("token", Base64.getEncoder().encodeToString(token.accessToken.toByteArray())).
                            setParameter("user", Base64.getEncoder().encodeToString(me.toByteArray()))
                        val uri = builder.toString()
                        logger.info("connect to circlet: $uri")
                        val session = connectToServer(uri) { cnctn, lifetime ->
                            logger.info("connected to ")
                            state.connection = cnctn
                            state.connectionState = ConnectionStates.Connected
                            lifetime.add {
                                disconnect()
                            }
                            connectingProgressLifetimeDef.terminate()
                        }
                        state.session = session
                    } catch (th: Throwable){
                        disconnect()
                        logger.error(th)
                    }
                } catch {
                    disconnect()
                    logger.error(it)
                }
            }
        }

    }

    fun disconnect(){
        // may disconned in both connected and connecting states.
        if (connectionState == null)
            return

        connectionState?.Close()
        connectionState = null
    }
}
