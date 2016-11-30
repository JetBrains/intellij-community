package circlet.login

import circlet.components.*
import circlet.reactive.*
import circlet.utils.*
import nl.komponents.kovenant.*
import runtime.lifetimes.*

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

    val refreshLifetimes = SequentialLifetimes(lifetime)

    init {
        login.view(lifetime, { lt, loginText ->
            pass.view(lt) { ltlt, passText ->
                val refreshLt = refreshLifetimes.next()
                loginStatus.value = LoginAuthStatus(LoginStatus.InProrgess, "")
                token.value = ""
                task {
                    loginComponent.getAccessToken(loginText, passText).thenLater(refreshLt) {
                        val errorMessage = it.errorMessage
                        if (errorMessage == null || errorMessage.isEmpty()) {
                            loginStatus.value = LoginAuthStatus(LoginStatus.Success, "")
                            token.value = it.token ?: ""
                        } else {
                            loginStatus.value = LoginAuthStatus(LoginStatus.Fail, errorMessage)
                        }
                    }.failureLater(refreshLt) {
                        loginStatus.value = LoginAuthStatus(LoginStatus.Fail, it.message!!)
                    }
                }
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
