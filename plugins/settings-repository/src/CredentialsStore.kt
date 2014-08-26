package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.transport.URIish
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper

public fun String?.nullize(): String? = StringUtil.nullize(this)

public fun showAuthenticationForm(credentials: Credentials?, uri: String, host: String?): Credentials? {
  val authenticationForm = RepositoryAuthenticationForm(credentials?.username, credentials?.password, if (host == "github.com") IcsBundle.message("login.github.note") else null)
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

public trait CredentialsStore {
  public fun get(uri: URIish): Credentials?

  public fun save(uri: URIish, credentials: Credentials)

  public fun reset(uri: URIish)
}