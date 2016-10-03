package circlet.components

import circlet.client.*
import circlet.client.auth.*
import circlet.utils.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.util.net.ssl.*
import klogging.*
import lifetime.*
import nl.komponents.kovenant.*
import org.apache.http.client.utils.*
import rx.subjects.*
import java.util.*

private val log = KLoggers.logger("plugin/IdePluginClient.kt")

class IdePluginClient(project : Project) : AbstractProjectComponent(project) {
    val DISPLAY_ID = "circlet.components.IdePluginClient"

    enum class ConnectionStates { Connecting, Connected }

    private val serverConfig = ServerConfig()
    var connectionState : IdePluginConnectionState? = null

    fun connect() {
        if (connectionState != null)
        {
            log.error("Can't connect twice")
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
            Thread.currentThread().contextClassLoader = this.javaClass.classLoader

            RingAuth(CertificateManager.getInstance().sslContext, CertificateManager.HOSTNAME_VERIFIER, serverConfig, connectedStateLifetimeDef.lifetime).apply {
                connectionState?.message = "Connecting to hub"
                hubInfo() thenAwait {
                    log.info("got hub info $it")
                    connectionState?.message = "Getting auth code"
                    authCode(it)
                } thenAwait {
                    log.info("got auth code")
                    connectionState?.message = "Getting refresh token"
                    refreshToken(it)
                } thenAwait {
                    log.info("got refresh token")
                    connectionState?.message = "Getting access token"
                    authToken(it)
                } then {
                    val me = user(it)

                    connectionState?.message = "Connecting to circlet server"
                    val token = it.accessTokenSource.accessToken
                    log.info("got access token: $token")
                    val builder = URIBuilder(serverConfig.serviceWebSocketUrl).
                        setParameter("token", Base64.getEncoder().encodeToString(token.accessToken.toByteArray())).
                        setParameter("user", Base64.getEncoder().encodeToString(me.toByteArray()))
                    val uri = builder.toString()
                    log.info("connect to circlet: $uri")

                    val session = log.io {
                        connectToServer(uri) { cnctn, lifetime ->
                            log.info("connected to ")
                            state.connection = cnctn
                            state.connectionState = ConnectionStates.Connected
                            lifetime.add {
                                disconnect()
                            }
                            connectingProgressLifetimeDef.terminate()
                        }
                    }
                    state.session = session

                } then {
                    // succcessful connection.
                    val notification = Notification(DISPLAY_ID, "Circlet", "Connected to server", NotificationType.INFORMATION)
                    Notifications.Bus.notify(notification, myProject)

                } catch { th ->
                    disconnect()
                    when (th){
                        is ProcessCancalledException ->{
                            val notification = Notification(DISPLAY_ID, "Circlet", "Connection cancelled", NotificationType.INFORMATION)
                            Notifications.Bus.notify(notification, myProject)
                        }
                        is ExpectedException -> {
                            val notification = Notification(DISPLAY_ID, "Circlet connection failed", th.message ?: "", NotificationType.ERROR)
                            Notifications.Bus.notify(notification, myProject)
                        }
                        else -> {
                            log.error(th)
                        }
                    }
                }
            }
        }

    }

    fun disconnect(){
        // may disconned in both connected and connecting states.

        val state = connectionState
        if (state == null)
            return

        connectionState = null

        // succcessful connection.
        val notification = Notification(DISPLAY_ID, "Circlet", "Disconnected from server", NotificationType.INFORMATION)
        Notifications.Bus.notify(notification, myProject)

        state.Close()
    }
}
