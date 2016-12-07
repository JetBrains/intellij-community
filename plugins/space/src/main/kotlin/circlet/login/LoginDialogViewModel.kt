package circlet.login

import circlet.components.*
import circlet.utils.*
import com.intellij.concurrency.*
import klogging.*
import nl.komponents.kovenant.*
import runtime.lifetimes.*
import runtime.reactive.*
import java.util.concurrent.*

private val log = KLoggers.logger("app-idea/LoginDialogViewModel.kt")

enum class LoginStatus {Success, Fail, InProrgess }

class LoginAuthStatus(val status: LoginStatus, val statusText: String) {
    fun presentStatus(): String =
        when (status) {
            LoginStatus.Success -> "Authorized"
            LoginStatus.Fail -> statusText
            LoginStatus.InProrgess -> "Checking credentials..."
        }
}

class LoginDialogViewModel(val loginComponent: CircletLoginComponent) {
    val lifetimeDef = Lifetime.create(Lifetime.Eternal)
    val lifetime = lifetimeDef.lifetime
    val loginStatus = Property(LoginAuthStatus(LoginStatus.InProrgess, ""))

    val login = Property(loginComponent.login)
    val pass = Property(loginComponent.pass)

    val token = Property("")
    val signInEnabled = Property(false)

    val refreshLifetimes = runtime.reactive.SequentialLifetimes(lifetime)

    init {
        login.view(lifetime, { lt, loginText ->
           pass.view(lt) { ltlt, passText ->
               loginStatus.value = LoginAuthStatus(LoginStatus.InProrgess, "")
               val refreshLt = refreshLifetimes.next()
                JobScheduler.getScheduler().schedule({
                    if (!refreshLt.isTerminated)
                    {
                        token.value = ""
                        task {
                            loginComponent.getAccessToken(loginText, passText).thenLater(refreshLt) {
                                log.info( "Checking credentials resulted in ${it.errorMessage}, ${it.errorTag}, ${it.token}" )
                                val errorMessage = it.errorMessage
                                if (errorMessage == null || errorMessage.isEmpty()) {
                                    loginStatus.value = LoginAuthStatus(LoginStatus.Success, "")
                                    token.value = it.token ?: ""
                                } else {
                                    loginStatus.value = LoginAuthStatus(LoginStatus.Fail, errorMessage)
                                }
                            }.failureLater(refreshLt) {
                                loginStatus.value = LoginAuthStatus(LoginStatus.Fail, it.message ?: "Failed to check credentials")
                            }
                        }
                    }
                }, 2000, TimeUnit.MILLISECONDS)
            }
        })
        loginStatus.view(lifetime) { _, status ->
            signInEnabled.value = status.status == LoginStatus.Success
        }
    }

    fun commit() {
        loginComponent.setCredentials(login.value, pass.value, token.value)
    }
}
