package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.jetbrains.plugins.settingsRepository.CredentialsStore
import org.jetbrains.plugins.settingsRepository.showAuthenticationForm
import org.jetbrains.plugins.settingsRepository.Credentials
import org.jetbrains.plugins.settingsRepository.nullize

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
    var passwordItem: CredentialItem? = null
    for (item in items) {
      if (item is CredentialItem.Username) {
        userNameItem = item
      }
      else if (item is CredentialItem.Password) {
        passwordItem = item
      }
      else if (item is CredentialItem.StringType) {
        if (item.getPromptText() == "Password: ") {
          passwordItem = item
          continue
        }
      }
    }
    return (userNameItem == null && passwordItem == null) || doGet(uri, userNameItem, passwordItem)
  }

  private fun doGet(uri: URIish, userNameItem: CredentialItem.Username?, passwordItem: CredentialItem?): Boolean {
    var credentials: Credentials?

    val userFromUri: String? = uri.getUser().nullize()
    val passwordFromUri: String? = uri.getPass().nullize()
    if (userFromUri != null && passwordFromUri != null) {
      credentials = Credentials(userFromUri, passwordFromUri)
    }
    else {
      credentials = credentialsStore.getValue().get(uri)

      if (userFromUri != null) {
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

      if (credentials != null) {
        credentialsStore.getValue().save(uri, credentials!!)
      }
    }

    userNameItem?.setValue(credentials?.username)
    if (passwordItem != null) {
      if (passwordItem is CredentialItem.Password) {
        passwordItem.setValue(credentials?.password?.toCharArray())
      }
      else {
        (passwordItem as CredentialItem.StringType).setValue(credentials?.password)
      }
    }

    return credentials != null
  }

  override fun reset(uri: URIish) {
    credentialsStore.getValue().reset(uri)
  }
}