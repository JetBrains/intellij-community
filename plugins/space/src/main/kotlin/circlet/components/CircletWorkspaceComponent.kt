package circlet.components

import circlet.arenas.initCircletArenas
import circlet.client.api.impl.ApiClassesDeserializer
import circlet.common.oauth.IdeaOAuthConfig
import circlet.permission.FeatureFlagsVmPersistenceKey
import circlet.platform.api.oauth.OAuthTokenResponse
import circlet.platform.api.oauth.toTokenInfo
import circlet.platform.api.serialization.ExtendableSerializationRegistry
import circlet.platform.workspaces.CodeFlowConfig
import circlet.platform.workspaces.WorkspaceConfiguration
import circlet.platform.workspaces.WorkspaceManagerHost
import circlet.runtime.ApplicationDispatcher
import circlet.settings.CircletServerSettings
import circlet.settings.CircletSettings
import circlet.settings.CircletSettingsPanel
import circlet.utils.IdeaPasswordSafePersistence
import circlet.utils.LifetimedDisposable
import circlet.utils.LifetimedDisposableImpl
import circlet.utils.notify
import circlet.workspaces.Workspace
import circlet.workspaces.WorkspaceManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.klogging.KLogger
import libraries.klogging.assert
import libraries.klogging.logger
import runtime.Ui
import runtime.mutableUiDispatch
import runtime.persistence.InMemoryPersistence
import runtime.persistence.PersistenceConfiguration
import runtime.persistence.PersistenceKey
import runtime.reactive.SequentialLifetimes
import runtime.reactive.flatMapInit
import runtime.reactive.mutableProperty
import runtime.utils.selectFreePort
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.coroutines.resume

// monitors CircletConfigurable state, creates and exposed instance of Workspace, provides various state properties and callbacks.
class CircletWorkspaceComponent : WorkspaceManagerHost(), LifetimedDisposable by LifetimedDisposableImpl() {
    private val log: KLogger = logger<CircletWorkspaceComponent>()

    private val ideaClientPersistenceConfiguration = PersistenceConfiguration(
        FeatureFlagsVmPersistenceKey,
        PersistenceKey.Arena
    )

    private val workspacesLifetimes = SequentialLifetimes(lifetime)

    private val manager = mutableProperty<WorkspaceManager?>(null)

    val workspace = flatMapInit<WorkspaceManager?, Workspace?>(manager, null) {
        (it?.workspace ?: mutableProperty<Workspace?>(null))
    }

    private val settings = CircletSettings.getInstance()

    init {
        initApp()
        val settingsOnStartup = settings.serverSettings
        val wsLifetime = workspacesLifetimes.next()

        // sign in automatically on application startup.
        launch(wsLifetime, Ui) {
            if (!autoSignIn(settingsOnStartup, wsLifetime)) {
                notifyDisconnected()
            }
        }
    }

    private fun initApp() {
        val application = ApplicationManager.getApplication()

        mutableUiDispatch = ApplicationDispatcher(application)

        initCircletArenas()

        ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
    }


    override suspend fun authFailed() {
        authCheckFailedNotification()
        manager.value?.signOut(false)
    }

    private val ports = lazy {
        val regex = "[^\\d]*(?<port>\\d+)[^\\d]*".toRegex()
        val ports = IdeaOAuthConfig.redirectURIs.mapNotNull { it ->
            regex.find(it)?.groups?.get("port")?.value?.toIntOrNull()
        }
        Pair(ports.min() ?: 1000, ports.max() ?: 65535)
    }

    suspend fun signIn(lifetime: Lifetime, server: String): OAuthTokenResponse {
        log.assert(manager.value == null, "manager.value == null")

        val lt = workspacesLifetimes.next()
        val wsConfig = ideaConfig(server)
        val wss = WorkspaceManager(lt, null, this, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClientPersistenceConfiguration,
                                   wsConfig)

        val port = selectFreePort(ports.value.first, ports.value.second)
        val authUrl = "http://localhost:$port/auth"
        val codeFlow = CodeFlowConfig(wsConfig, authUrl)

        val response: OAuthTokenResponse = suspendCancellableCoroutine { cnt ->
            launch(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
                ServerSocket(port).use { serverSocket ->
                    val socket: Socket = serverSocket.accept()
                    socket.getInputStream().use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line = reader.readLine()

                            line = line.substringAfter("/").substringBefore(" ")
                            val token = codeFlow.handleCodeFlowRedirect("/$line")
                            cnt.resume(token)

                            PrintWriter(socket.getOutputStream()).use { out ->
                                val response = "<script>close()</script>"
                                out.println("HTTP/1.1 200 OK")
                                out.println("Content-Type: text/html")
                                out.println("Content-Length: " + response.length)
                                out.println()
                                out.println(response)
                                out.flush()
                            }
                        }
                    }
                }
            }

            try {
                val uri = URI(codeFlow.codeFlowURL())
                Desktop.getDesktop().browse(uri)
            }
            catch (th: Throwable) {
                val message = "Can't open '${wsConfig.server}' in system browser"
                log.warn(th, message)
                cnt.resume(OAuthTokenResponse.Error(wsConfig.server, "", "Can't open '${wsConfig.server}' in system browser."))
            }
        }

        if (response is OAuthTokenResponse.Success) {
            log.info { "response = ${response.accessToken} ${response.expiresIn} ${response.refreshToken} ${response.scope}" }
            wss.signInWithToken(response.toTokenInfo())
            settings.serverSettings = CircletServerSettings(true, server)
            manager.value = wss
            notifyConnected()
        }
        return response
    }

    fun signOut() {
        val oldManager = manager.value
        oldManager?.signOut(true)
        workspacesLifetimes.clear()
        manager.value = null
        settings.serverSettings = settings.serverSettings.copy(enabled = false)
    }

    private suspend fun autoSignIn(settingsOnStartup: CircletServerSettings, wsLifetime: Lifetime): Boolean {
        if (settingsOnStartup.server.isNotBlank() && settingsOnStartup.enabled) {
            val wsConfig = ideaConfig(settingsOnStartup.server)
            val wss = WorkspaceManager(wsLifetime, null, this@CircletWorkspaceComponent, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClientPersistenceConfiguration, wsConfig)
            if (wss.signInNonInteractive()) {
                manager.value = wss
                return true
            }
        }
        return false
    }

}

val circletWorkspace: CircletWorkspaceComponent
    get() = ServiceManager.getService(CircletWorkspaceComponent::class.java)


private fun notifyDisconnected() {
    notify("Disconnected.<br><a href=\"switch-on\">Configure Server</a>", ::configure)
}

private fun notifyConnected() {
    notify("Connected")
}

private fun authCheckFailedNotification() {
    notify("Not authenticated.<br> <a href=\"sign-in\">Sign in</a>") {
    }
}

private fun configure() {
    CircletSettingsPanel.openSettings(null)
}

fun ideaConfig(server: String): WorkspaceConfiguration {
    return WorkspaceConfiguration(
        server,
        IdeaOAuthConfig.clientId,
        IdeaOAuthConfig.clientSecret)
}

