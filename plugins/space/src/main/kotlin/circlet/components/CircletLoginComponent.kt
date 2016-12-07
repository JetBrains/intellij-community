package circlet.components

import circlet.*
import circlet.reactive.*
import circlet.utils.*
import com.intellij.ide.passwordSafe.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import klogging.*
import runtime.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/CircletLoginComponent.kt")

data class IdePLuginClientData (
    var myEnabled : Boolean? = null
)

val stagingEndpoint = "172.20.165.72:30896"
val localEndpoint = "127.0.0.1:8084"
val endpoint = localEndpoint

@State(
    name = "CircletLoginComponent",
    storages = arrayOf(Storage(
        value = "CircletClient.xml",
        roamingType = RoamingType.DISABLED)))
class CircletLoginComponent() :
    ILifetimedApplicationComponent by LifetimedApplicationComponent(),
    PersistentStateComponent<IdePLuginClientData>{
    val authEndpoint = "http://$endpoint/api/v1/authenticate"

    val LOGIN_ID = "CircletLoginComponent.login"
    val PASS_ID = "CircletLoginComponent.pass"
    val TOKEN_ID = "CircletLoginComponent.token"

    private val passwords = PasswordSafe.getInstance()

    val login : String get() = passwords.getPassword(ProjectManager.getInstance().defaultProject, this.javaClass, LOGIN_ID).orEmpty()
    val pass : String get() = passwords.getPassword(ProjectManager.getInstance().defaultProject, this.javaClass, PASS_ID).orEmpty()
    val token : String get() = passwords.getPassword(ProjectManager.getInstance().defaultProject, this.javaClass, TOKEN_ID).orEmpty()
    val credentialsUpdated = Signal<Boolean>()
    val enabled = Property(false)

    fun getAccessToken(login : String, pass : String) : Promise<AuthenticationResponse> {
        log.info( "Checking credentials for ${login}" )
        return CircletAuthentication(authEndpoint).authenticate(login, pass)
    }

    fun setCredentials(login: String, pass: String, token: String) {
        passwords.storePassword(ProjectManager.getInstance().defaultProject, this.javaClass, LOGIN_ID, login)
        passwords.storePassword(ProjectManager.getInstance().defaultProject, this.javaClass, PASS_ID, pass)
        passwords.storePassword(ProjectManager.getInstance().defaultProject, this.javaClass, TOKEN_ID, token)
        credentialsUpdated.fire(true)
    }


    override fun loadState(state: IdePLuginClientData) {
        enabled.value = state.myEnabled ?: false
    }

    override fun getState(): IdePLuginClientData =
        IdePLuginClientData(enabled.value)

}
