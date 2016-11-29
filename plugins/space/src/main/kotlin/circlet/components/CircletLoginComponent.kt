package circlet.components

import circlet.reactive.*
import circlet.utils.*
import com.intellij.ide.passwordSafe.*
import com.intellij.openapi.project.*

class JetBrainsAccountLoginData(val login: String, val pass : String, val token: String)

class CircletLoginComponent(val project : Project) : ILifetimedComponent by LifetimedComponent(project) {

    val LOGIN_ID = "CircletLoginComponent.login"
    val PASS_ID = "CircletLoginComponent.pass"
    val KEY_ID = "CircletLoginComponent.key"

    private val passwords = PasswordSafe.getInstance()
    val loginData = Property(JetBrainsAccountLoginData("", "", ""))

    init {
        loginData.value = JetBrainsAccountLoginData(
            passwords.getPassword(project, this.javaClass, LOGIN_ID).orEmpty(),
            passwords.getPassword(project, this.javaClass, PASS_ID).orEmpty(),
            passwords.getPassword(project, this.javaClass, KEY_ID).orEmpty())

        loginData.view(componentLifetime) { lt, data ->
            passwords.storePassword(project, this.javaClass, LOGIN_ID, data.login)
            passwords.storePassword(project, this.javaClass, PASS_ID, data.pass)
            passwords.storePassword(project, this.javaClass, KEY_ID, data.token)
        }

    }

    fun isEmpty() =
        loginData.value.login.isEmpty()
            || loginData.value.pass.isEmpty()
            || loginData.value.token.isEmpty()

}
