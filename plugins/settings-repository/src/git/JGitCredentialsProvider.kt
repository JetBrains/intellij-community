package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.util.NotNullLazyValue
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.jetbrains.plugins.settingsRepository.CredentialsStore
import org.jetbrains.plugins.settingsRepository.showAuthenticationForm
import org.jetbrains.plugins.settingsRepository.Credentials
import org.jetbrains.plugins.settingsRepository.nullize
import org.jetbrains.plugins.settingsRepository.isFulfilled
import org.eclipse.jgit.lib.Repository

class JGitCredentialsProvider(private val credentialsStore: NotNullLazyValue<CredentialsStore>, private val repository: Repository) : CredentialsProvider() {
  private var credentialsFromGit: Credentials? = null

  override fun isInteractive() = true

  override fun supports(vararg items: CredentialItem?): Boolean {
    for (item in items) {
      if (item is CredentialItem.Password || item is CredentialItem.Username || item is CredentialItem.StringType) {
        continue
      }
      return false
    }
    return true
  }

  override fun get(uri: URIish, vararg items: CredentialItem?): Boolean {
    var userNameItem: CredentialItem.Username? = null
    var passwordItem: CredentialItem? = null
    var sshKeyFile: String? = null
    for (item in items) {
      if (item is CredentialItem.Username) {
        userNameItem = item
      }
      else if (item is CredentialItem.Password) {
        passwordItem = item
      }
      else if (item is CredentialItem.StringType) {
        val promptText = item.getPromptText()
        if (promptText != null) {
          val marker = "Passphrase for "
          if (promptText.startsWith(marker) /* JSch prompt */) {
            sshKeyFile = promptText.substring(marker.length())
            passwordItem = item
            continue
          }
        }
      }
    }
    return (userNameItem == null && passwordItem == null) || doGet(uri, userNameItem, passwordItem, sshKeyFile)
  }

  private fun doGet(uri: URIish, userNameItem: CredentialItem.Username?, passwordItem: CredentialItem?, sshKeyFile: String?): Boolean {
    var credentials: Credentials?

    val userFromUri: String? = uri.getUser().nullize()
    val passwordFromUri: String? = uri.getPass().nullize()
    var saveCredentialsToStore = false
    if (userFromUri != null && passwordFromUri != null) {
      credentials = Credentials(userFromUri, passwordFromUri)
    }
    else {
      if (credentialsFromGit == null) {
        credentialsFromGit = getCredentialsUsingGit(uri, repository)
      }
      credentials = credentialsFromGit

      if (credentials == null) {
        credentials = credentialsStore.getValue().get(uri.getHost(), sshKeyFile)
        saveCredentialsToStore = true

        if (userFromUri != null) {
          // username is in url - read password only if it is for the same user
          if (userFromUri != credentials?.username) {
            credentials = Credentials(userFromUri, passwordFromUri)
          }
          else if (passwordFromUri != null && passwordFromUri != credentials?.password) {
            credentials = Credentials(userFromUri, passwordFromUri)
          }
        }
      }
    }

    if (!credentials.isFulfilled()) {
      credentials = showAuthenticationForm(credentials, uri.toString(), uri.getHost())
    }

    if (saveCredentialsToStore && credentials.isFulfilled()) {
      credentialsStore.getValue().save(uri.getHost(), credentials!!, sshKeyFile)
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
    credentialsFromGit = null
    credentialsStore.getValue().reset(uri)
  }
}
