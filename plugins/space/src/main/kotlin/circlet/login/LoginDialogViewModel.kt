package circlet.login

import circlet.*
import circlet.components.*
import circlet.utils.*
import com.intellij.concurrency.*
import klogging.*
import runtime.async.*
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
    val lifetimeDef = Lifetime()
    val lifetime = lifetimeDef
    val loginStatus = Property.createMutable(LoginAuthStatus(LoginStatus.InProrgess, ""))

    val url = Property.createMutable("")
    val orgName = Property.createMutable("")
    val login = Property.createMutable("")
    val pass = Property.createMutable("")

    val signInEnabled = Property.createMutable(false)
    val refreshLifetimes = SequentialLifetimes(lifetime)

    init {
        orgName.value = loginComponent.orgName.value
        login.value = loginComponent.login.value
        url.value = loginComponent.url.value
        login.view(lifetime, { lt, loginText ->
            pass.view(lt) { ltlt, passText ->
                url.view(ltlt) { ltltlt, urlText ->
                    orgName.view(ltltlt) { ltltltlt, urlText ->
                        loginStatus.value = LoginAuthStatus(LoginStatus.InProrgess, "")
                        val refreshLt = refreshLifetimes.next()
                        JobScheduler.getScheduler().schedule({
                            if (!refreshLt.isTerminated) {
                                async {
                                    try {
                                        getToken(ltltltlt)
                                        loginStatus.value = LoginAuthStatus(LoginStatus.Success, "")
                                    } catch (ex: Throwable) {
                                        loginStatus.value = LoginAuthStatus(LoginStatus.Fail, ex.message ?: "Failed to check credentials")
                                    }
                                }
                            }
                        }, 1000, TimeUnit.MILLISECONDS)
                    }
                }
            }
        })
        loginStatus.view(lifetime) { _, status ->
            signInEnabled.value = status.status == LoginStatus.Success
        }
    }

    private suspend fun getToken(lt: Lifetime): String {
        val client = CircletClient(lt)
        client.start(SandboxPersistence, url.value, orgName.value)
        return client.tryLogin(login.value, pass.value).raw
    }

    fun commit() {
        async {
            Lifetime().apply {
                try {
                    val token = getToken(this)
                    IdeaPersistence.put("circlet_token", token)
                } catch (th: Throwable) {
                    loginComponent.url.value = url.value
                    loginComponent.orgName.value = orgName.value
                    loginComponent.login.value = login.value
                    IdeaPersistence.delete("circlet_token")
                    loginComponent.token.value++
                    return@apply
                }
                loginComponent.url.value = url.value
                loginComponent.orgName.value = orgName.value
                loginComponent.login.value = login.value
                loginComponent.token.value++
            }
        }
    }
}
