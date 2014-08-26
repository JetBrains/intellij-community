package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.jetbrains.plugins.settingsRepository.CredentialsStore
import org.jetbrains.plugins.settingsRepository.showAuthenticationForm
import org.jetbrains.plugins.settingsRepository.Credentials

class JGitCredentialsProvider(private val credentialsStore: NotNullLazyValue<CredentialsStore>) : CredentialsProvider() {
  override fun isInteractive() = true

  override fun supports(vararg items: CredentialItem?): Boolean {
    for (item in items) {
      if (item is CredentialItem.Password || item is CredentialItem.Username) {
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
    var credentials = credentialsStore.getValue().get(uri)

    var userFromUri: String? = uri.getUser()
    var passwordFromUri: String?
    if (userFromUri != null) {
      passwordFromUri = StringUtil.nullize(uri.getPass(), true)
      // username is in url - read password only if it is for the same user
      if (userFromUri != credentials?.username) {
        credentials = Credentials(userFromUri, passwordFromUri)
      }
      else if (passwordFromUri != null && passwordFromUri != credentials?.password) {
        credentials = Credentials(userFromUri, passwordFromUri)
      }
    }



    if (credentials?.username == null || credentials?.password == null) {
      credentials = showAuthenticationForm(credentials, uri.toString(), uri.getHost())
    }

    userNameItem.setValue(credentials?.username)
    passwordItem.setValue(credentials?.password?.toCharArray())
    if (credentials != null) {
      credentialsStore.getValue().save(uri, credentials!!)
    }
    return credentials != null
  }

  override fun reset(uri: URIish) {
    credentialsStore.getValue().reset(uri)
  }
}