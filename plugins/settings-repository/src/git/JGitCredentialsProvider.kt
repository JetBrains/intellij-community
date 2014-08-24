package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.UIUtil
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.jetbrains.plugins.settingsRepository.AuthDialog
import org.jetbrains.plugins.settingsRepository.CredentialsStore

class JGitCredentialsProvider(private val credentialsStore: NotNullLazyValue<CredentialsStore>) : CredentialsProvider() {
  override fun isInteractive(): Boolean {
    return true
  }

  override fun supports(vararg items: CredentialItem?): Boolean {
    for (item in items) {
      if (item is CredentialItem.Password) {
        continue
      }
      if (item is CredentialItem.Username) {
        continue
      }
      return false
    }
    return true
  }

  override fun get(uri: URIish, vararg items: CredentialItem?): Boolean {
    var userNameItem: CredentialItem.Username? = null
    var passwordItem: CredentialItem.Password? = null
    for (item in items) {
      if (item is CredentialItem.Username) {
        userNameItem = item as CredentialItem.Username
      }
      else if (item is CredentialItem.Password) {
        passwordItem = item as CredentialItem.Password
      }
    }
    return (userNameItem == null && passwordItem == null) || doGet(uri, userNameItem!!, passwordItem!!)
  }

  private fun doGet(uri: URIish, userNameItem: CredentialItem.Username, passwordItem: CredentialItem.Password): Boolean {
    val credentials = credentialsStore.getValue().get(uri)

    var user: String? = uri.getUser()
    var password: String?
    if (user == null) {
      // username is not in the url - reading pre-filled value from the password storage
      user = credentials?.username
      password = credentials?.password
    }
    else {
      password = StringUtil.nullize(uri.getPass(), true)
      // username is in url - read password only if it is for the same user
      if (password == null && user == credentials?.username) {
        password = credentials?.password
      }
    }

    val ok: Boolean
    if (user != null && password != null) {
      ok = true
    }
    else {
      var dialog: AuthDialog? = null
      UIUtil.invokeAndWaitIfNeeded(object : Runnable {
        override fun run() {
          dialog = AuthDialog("Login required", "Login to " + uri, user, password)
          dialog!!.show()
        }
      })
      ok = dialog!!.isOK()
      if (ok) {
        user = dialog!!.getUsername()
        password = dialog!!.getPassword()
        if (StringUtil.isEmptyOrSpaces(password)) {
          password = "x-oauth-basic"
        }
      }
    }

    if (ok) {
      userNameItem.setValue(user)
      passwordItem.setValue(password!!.toCharArray())
      credentialsStore.getValue().save(uri, CredentialsStore.Credentials(user, password))
    }
    return ok
  }

  override fun reset(uri: URIish) {
    credentialsStore.getValue().reset(uri)
  }
}