package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.transport.URIish
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.application.ApplicationManager

public fun String?.nullize(): String? = StringUtil.nullize(this)

public fun showAuthenticationForm(credentials: Credentials?, uri: String, host: String?): Credentials? {
  if (ApplicationManager.getApplication()?.isUnitTestMode() === true) {
    throw AssertionError("showAuthenticationForm called from tests")
  }

  val authenticationForm = RepositoryAuthenticationForm(credentials?.username, credentials?.password, IcsBundle.message(if (host == "github.com") "login.github.note" else "login.other.git.provider.note"))
  var ok = false
  UIUtil.invokeAndWaitIfNeeded(object : Runnable {
    override fun run() {
      ok = DialogBuilder().title("Log in to " + StringUtil.trimMiddle(uri.toString(), 50)).centerPanel(authenticationForm.getPanel()).show() == DialogWrapper.OK_EXIT_CODE
    }
  })
  if (ok) {
    val passwordChars = authenticationForm.getPassword()
    val username = authenticationForm.getUsername()
    return Credentials(username, if (passwordChars == null) (if (username == null) null else "x-oauth-basic") else String(passwordChars))
  }
  return null
}

public data class Credentials(username: String?, password: String?) {
  public val username: String? = username.nullize()
  public val password: String? = password.nullize()
}

public fun Credentials?.isFulfilled(): Boolean = this != null && username != null && password != null

public trait CredentialsStore {
  public fun get(host: String?, sshKeyFile: String? = null): Credentials?

  public fun save(host: String?, credentials: Credentials, sshKeyFile: String? = null)

  public fun reset(uri: URIish)
}
