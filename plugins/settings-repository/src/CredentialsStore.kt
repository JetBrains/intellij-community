package org.jetbrains.settingsRepository

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.transport.URIish
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PathUtilRt

public fun String?.nullize(): String? = StringUtil.nullize(this)

public fun showAuthenticationForm(credentials: Credentials?, uri: String, host: String?, sshKeyFile: String?): Credentials? {
  if (ApplicationManager.getApplication()?.isUnitTestMode() === true) {
    throw AssertionError("showAuthenticationForm called from tests")
  }

  var filledCredentials: Credentials? = null
  UIUtil.invokeAndWaitIfNeeded(object : Runnable {
    override fun run() {
      val note = if (sshKeyFile == null) IcsBundle.message(if (host == "github.com") "login.github.note" else "login.other.git.provider.note") else null
      val authenticationForm = RepositoryAuthenticationForm(if (sshKeyFile == null) {
        IcsBundle.message("log.in.to", StringUtil.trimMiddle(uri, 50))
      }
      else {
        IcsBundle.message("enter.your.password.for.ssh.key", PathUtilRt.getFileName(sshKeyFile))
      }, credentials?.username, credentials?.password, note, sshKeyFile != null)
      if (authenticationForm.showAndGet()) {
        val username = sshKeyFile ?: authenticationForm.getUsername()
        val passwordChars = authenticationForm.getPassword()
        filledCredentials = Credentials(username, if (passwordChars == null) (if (username == null) null else "x-oauth-basic") else String(passwordChars))
      }
    }
  })
  return filledCredentials
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
