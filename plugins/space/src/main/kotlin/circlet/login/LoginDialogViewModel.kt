package circlet.login

import circlet.components.*
import circlet.utils.*
import com.intellij.concurrency.*
import klogging.*
import nl.komponents.kovenant.*
import runtime.kdata.*
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
    val lifetime = BindingContext()
    val loginStatus = Property.createMutable(LoginAuthStatus(LoginStatus.InProrgess, ""))

    val login = Property.createMutable(loginComponent.login)
    val pass = Property.createMutable(loginComponent.pass)

    val token = Property.createMutable("")
    val signInEnabled = Property.createMutable(false)

    // val refreshLifetimes = runtime.reactive.SequentialLifetimes(lifetime)

    init {
        login.forEach(lifetime) {
            pass.forEach(lifetime) { passText ->
                loginStatus.value = LoginAuthStatus(LoginStatus.InProrgess, "")
                // val refreshLt = refreshLifetimes.next()
//                JobScheduler.getScheduler().schedule({
//                    if (!refreshLt.isTerminated) {
//                        token.value = ""
//                        task {
//                            loginComponent.getAccessToken(loginText, passText).thenLater(refreshLt) {
//                                log.info("Checking credentials resulted in ${it.errorMessage}, ${it.errorTag}, ${it.token}")
//                                val errorMessage = it.errorMessage
//                                if (errorMessage == null || errorMessage.isEmpty()) {
//                                    loginStatus.value = LoginAuthStatus(LoginStatus.Success, "")
//                                    token.value = it.token ?: ""
//                                } else {
//                                    loginStatus.value = LoginAuthStatus(LoginStatus.Fail, errorMessage)
//                                }
//                            }.failureLater(refreshLt) {
//                                loginStatus.value = LoginAuthStatus(LoginStatus.Fail, it.message ?: "Failed to check credentials")
//                            }
//                        }
//                    }
//                }, 2000, TimeUnit.MILLISECONDS)
            }
        }
        loginStatus.forEach(lifetime) {
            signInEnabled.value = it.status == LoginStatus.Success
        }
    }

    fun commit() {
        loginComponent.setCredentials(login.value, pass.value, token.value)
    }
}
