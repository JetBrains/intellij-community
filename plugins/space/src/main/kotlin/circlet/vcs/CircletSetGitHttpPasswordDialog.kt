package circlet.vcs

import circlet.client.api.*
import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.*
import com.intellij.openapi.*
import com.intellij.openapi.rd.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.panels.*
import com.intellij.ui.layout.*
import com.intellij.util.*
import com.intellij.util.io.*
import com.intellij.util.ui.*
import git4idea.commands.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import java.util.concurrent.*
import javax.swing.*

internal class CircletSetGitHttpPasswordDialog(
    private val me: TD_MemberProfile,
    private val td: TeamDirectory,
    private val repoService: RepositoryService
) : DialogWrapper(null, false) {
    private val log = logger<CircletSetGitHttpPasswordDialog>()

    private val lifetime: LifetimeSource = LifetimeSource()

    internal var result: CircletHttpPasswordState = CircletHttpPasswordState.NotSet

    private val passwordSafe: PasswordSafe = PasswordSafe.instance

    private val passwordField: JBPasswordField = JBPasswordField()
    private val rememberPassword: JCheckBox = JCheckBox(UIBundle.message("auth.remember.cb"), passwordSafe.isRememberPasswordByDefault)

    private val asyncProcessIcon = AsyncProcessIcon("Set password").apply {
        isVisible = false
    }

    init {
        title = "Set Git HTTP password"
        setOKButtonText("Save")
        init()
        Disposer.register(disposable, Disposable { lifetime.terminate() })
    }

    override fun doOKAction() {
        launch(lifetime, Ui) {
            okAction.isEnabled = false
            asyncProcessIcon.isVisible = true

            lifetime.usingSource {
                try {
                    val password = passwordField.password
                    log.info("Trying to set HTTP Git password")
                    td.setVcsPassword(me.id, String(password))
                    val httpPassword = td.getVcsPassword(me.id)
                    log.info("Password set")

                    result = if (httpPassword == null) CircletHttpPasswordState.NotSet else CircletHttpPasswordState.Set(httpPassword)

                    if (rememberPassword.isSelected) {
                        val username = me.username
                        val httpUrl = repoService.getRepoUrlPatterns().httpUrl

                        if (httpUrl != null) {
                            val key = makeKey(getGitUrlHost(httpUrl), username)
                            val credentialAttributes = CredentialAttributes(generateServiceName("Git HTTP", key), key, GitHttpAuthenticator::class.java)
                            val credentials = Credentials(username, password)
                            passwordSafe.set(credentialAttributes, credentials)
                        }
                    }

                    close(OK_EXIT_CODE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RpcException) {
                    log.error(e, e.failure.message())
                    setErrorText(e.failure.message())
                } catch (e: Exception) {
                    log.error(e, "Unable to set password")
                    setErrorText("Unable to set password")
                }
            }

            asyncProcessIcon.isVisible = false
            okAction.isEnabled = true
        }
    }

    override fun createCenterPanel(): JComponent? = panel {
        row("Username:") {
            JBTextField(me.username)().component.apply {
                isEditable = false
            }
        }
        row(UIBundle.message("auth.password.label")) {
            passwordField().focused()
        }
        row {
            rememberPassword()
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (passwordField.password.isEmpty()) {
            return ValidationInfo("Password shouldn't be empty", passwordField)
        }
        return null
    }

    override fun createSouthPanel(): JComponent {
        val buttons = super.createSouthPanel()
        return JPanel(HorizontalLayout(JBUI.scale(8), SwingConstants.BOTTOM)).apply {
            asyncProcessIcon.border = buttons.border
            add(asyncProcessIcon, HorizontalLayout.RIGHT)
            add(buttons, HorizontalLayout.RIGHT)
        }
    }
}

fun makeKey(url: String, login: String): String {
    val pair = UriUtil.splitScheme(url)
    val scheme: String = pair.getFirst()
    return if (!StringUtil.isEmpty(scheme)) {
        scheme + URLUtil.SCHEME_SEPARATOR + login + "@" + pair.getSecond()
    }
    else "$login@$url"
}

fun getGitUrlHost(url: String): String {
    val host = url.substringAfter("://").substringBefore("/")
    return "http://$host"
}
