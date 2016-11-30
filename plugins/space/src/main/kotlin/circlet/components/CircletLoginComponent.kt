package circlet.components

import circlet.*
import circlet.reactive.*
import circlet.utils.*
import com.intellij.ide.passwordSafe.*
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.project.*
import klogging.*
import runtime.*
import runtime.lifetimes.*

class JetBrainsAccountLoginData(val login: String, val pass : String, val token: String)

private val log = KLoggers.logger("app-idea/CircletLoginComponent.kt")

class CircletLoginComponent(val project : Project) : ILifetimedComponent by LifetimedComponent(project) {
    val authUrl = "http://localhost:8084/api/v1/authenticate"

    val LOGIN_ID = "CircletLoginComponent.login"
    val PASS_ID = "CircletLoginComponent.pass"
    val TOKEN_ID = "CircletLoginComponent.token"

    private val passwords = PasswordSafe.getInstance()

    val login : String get() = passwords.getPassword(project, this.javaClass, LOGIN_ID).orEmpty()
    val pass : String get() = passwords.getPassword(project, this.javaClass, PASS_ID).orEmpty()
    val token : String get() = passwords.getPassword(project, this.javaClass, TOKEN_ID).orEmpty()

    val credentialsUpdated = Signal<Boolean>()

    fun getAccessToken(login : String, pass : String) : Promise<AuthenticationResponse> {
        Thread.sleep(2000)
        log.info( "Checking credentials for ${login}" )
        return CircletAuthentication(authUrl).authenticate(login, pass)
    }

    fun setCredentials(login: String, pass: String, token: String) {
        passwords.storePassword(project, this.javaClass, LOGIN_ID, login)
        passwords.storePassword(project, this.javaClass, PASS_ID, pass)
        passwords.storePassword(project, this.javaClass, TOKEN_ID, token)
        credentialsUpdated.fire(true)
    }


}
