package circlet.vcs

import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.client.api.impl.vcsPasswords
import circlet.client.repoService
import circlet.platform.client.KCircletClient
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.layout.*
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import git4idea.commands.GitHttpAuthenticator
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import libraries.klogging.logger
import runtime.RpcException
import runtime.Ui
import runtime.message
import java.util.concurrent.CancellationException
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class CircletSetGitHttpPasswordDialog(
  private val me: TD_MemberProfile,
  private val client: KCircletClient
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
                    client.api.vcsPasswords().setVcsPassword(me.identifier, String(password))
                    val httpPassword = client.api.vcsPasswords().getVcsPassword(me.identifier)
                    log.info("Password set")

                    result = if (httpPassword == null) CircletHttpPasswordState.NotSet else CircletHttpPasswordState.Set(httpPassword)

                    if (rememberPassword.isSelected) {
                        val username = me.username
                        val repoService = client.repoService
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
