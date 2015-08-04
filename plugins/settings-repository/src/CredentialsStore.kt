package org.jetbrains.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.ui.UIUtil
import org.jetbrains.keychain.Credentials

public fun showAuthenticationForm(credentials: Credentials?, uri: String, host: String?, path: String?, sshKeyFile: String?): Credentials? {
  if (ApplicationManager.getApplication()?.isUnitTestMode() === true) {
    throw AssertionError("showAuthenticationForm called from tests")
  }

  return UIUtil.invokeAndWaitIfNeeded(object : Computable<Credentials?> {
    override fun compute(): Credentials? {
      val note = if (sshKeyFile == null) IcsBundle.message(if (host == "github.com") "login.github.note" else "login.other.git.provider.note") else null
      var username = credentials?.id
      if (username == null && host == "github.com" && path != null && sshKeyFile == null) {
        val firstSlashIndex = path.indexOf('/', 1)
        username = path.substring(1, if (firstSlashIndex == -1) path.length() else firstSlashIndex)
      }

      val authenticationForm = RepositoryAuthenticationForm(if (sshKeyFile == null) {
        IcsBundle.message("log.in.to", StringUtil.trimMiddle(uri, 50))
      }
      else {
        IcsBundle.message("enter.your.password.for.ssh.key", PathUtilRt.getFileName(sshKeyFile))
      }, username, credentials?.token, note, sshKeyFile != null)
      if (authenticationForm.showAndGet()) {
        username = sshKeyFile ?: authenticationForm.getUsername()
        val passwordChars = authenticationForm.getPassword()
        return Credentials(username, if (passwordChars == null) (if (username == null) null else "x-oauth-basic") else String(passwordChars))
      }
      else {
        return null
      }
    }
  })
}