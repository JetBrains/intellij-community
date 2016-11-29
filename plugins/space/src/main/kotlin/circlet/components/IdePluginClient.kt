package circlet.components

import circlet.*
import circlet.login.*
import circlet.reactive.*
import circlet.utils.*
import com.intellij.concurrency.*
import com.intellij.ide.passwordSafe.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import klogging.*
import nl.komponents.kovenant.*
import org.joda.time.*
import runtime.*
import runtime.lifetimes.*

private val log = KLoggers.logger("plugin/IdePluginClient.kt")

enum class ConnectingState {
    Disconnect,
    Connected,
    AuthFailed,
    TryConnect
}

class IdePLuginClientData(val enabled : Boolean) {}

@State(name = "IdePluginClient", storages = arrayOf(Storage(StoragePathMacros.WORKSPACE_FILE)))
class IdePluginClient(project : Project) :
    AbstractProjectComponent(project),
    PersistentStateComponent<IdePLuginClientData>,
    ILifetimedComponent by LifetimedComponent(project) {

    val state = Property(ConnectingState.Disconnect)
    val enabled = Property(false)
    val askPasswordExplicitlyAllowed = Property(false)
    val client = Property<CircletClient?>(null)

    init {
        enabled.whenTrue(componentLifetime) { enabledLt ->
            state.value = ConnectingState.TryConnect

            enabledLt.add {
                disconnect()
            }
        }

        state.view(componentLifetime) { lt, state ->
            when(state){
                ConnectingState.TryConnect -> tryReconnect(lt)
                ConnectingState.AuthFailed -> {

                }
                else -> {

                }
            }
        }

/*
        ProgressManager.getInstance().run(externalTask(myProject, true, object: IExternalTask {
            override val lifetime: Lifetime get() = connectingProgressLifetimeDef.lifetime
            override val cancel: () -> Unit get() = { disconnect() }
            override val title: String get() = connectionState?.message ?: "Connecting"
            override val header: String get() = connectionState?.message ?: "Connecting"
            override val description: String get() = connectionState?.message ?: "Connecting"
            override val isIndeterminate: Boolean get() = true
            override val progress: Double get() = 0.5
        }))
*/
    }

    fun enable() {
        askPasswordExplicitlyAllowed.value = true
        enabled.value = true
    }

    fun disable() {
        enabled.value = false
    }

    private fun tryReconnect(lifetime: Lifetime) {
        val loginComponent = myProject.getComponent<CircletLoginComponent>()
        if (loginComponent.isEmpty())
        {
            if (askPasswordExplicitlyAllowed.value)
                askPassword(loginComponent)
        }
        connectWithToken(lifetime, loginComponent.loginData.value.token)
    }

    private fun askPassword(loginComponent: CircletLoginComponent) {
        LoginDialog(loginComponent).show()


        val auth = CircletAuthentication("http://localhost:8084/api/v1/authenticate")
        if (loginComponent.loginData.value.token.isEmpty()) {
            auth.authenticate(loginComponent.loginData.value.login, loginComponent.loginData.value.login).then {
                loginComponent.loginData.value = JetBrainsAccountLoginData(
                    loginComponent.loginData.value.login,
                    loginComponent.loginData.value.pass,
                    it
                )
            }.failure {
                loginComponent.loginData.value = JetBrainsAccountLoginData(
                    loginComponent.loginData.value.login,
                    loginComponent.loginData.value.pass,
                    ""
                )
                state
                log.info { "Error: $it" }
            }
        }
    }

    private fun connectWithToken(lifetime : Lifetime, token: String) {
        val clientLocal = CircletClient("ws://localhost:8084/api/v1/connect", token)
        client.value = clientLocal
        // check if token is correct???

        clientLocal.profile.isMyProfileReady()
            .flatMap {
                if (!it) {
                    clientLocal.profile.createMyProfile("Hey! ${Random.nextBytes(10)}")
                } else {
                    clientLocal.profile.getMyProfile().map {
                        log.warn { "My Profile: $it" }
                    }
                    clientLocal.profile.editMyName("Hey! ${Random.nextBytes(10)}")
                }
            }
            // .flatMap { client.profile.editMyName("Hey! ${Random.nextBytes(10)}") }
            .then {
                log.warn { "Result: $it" }
            }
            .failure {
                log.warn { "Error: $it" }
            }
   }

    fun disconnect(){

        state.value = ConnectingState.Disconnect

        val notification = Notification(
            "IdePluginClient.ID",
            "Circlet",
            "Disconnected from server",
            NotificationType.INFORMATION)

        Notifications.Bus.notify(notification, myProject)
    }

    override fun loadState(state: IdePLuginClientData) {}
    override fun getState(): IdePLuginClientData = IdePLuginClientData(enabled.value)

}


