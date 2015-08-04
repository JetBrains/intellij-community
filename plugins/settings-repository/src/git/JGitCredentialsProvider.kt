package org.jetbrains.settingsRepository.git

import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.ui.UIUtil
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.jetbrains.keychain.Credentials
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.keychain.isFulfilled
import org.jetbrains.keychain.isOSXCredentialsStoreSupported
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.nullize
import org.jetbrains.settingsRepository.showAuthenticationForm

class JGitCredentialsProvider(private val credentialsStore: NotNullLazyValue<CredentialsStore>, private val repository: Repository) : CredentialsProvider() {
  private var credentialsFromGit: Credentials? = null

  override fun isInteractive() = true

  override fun supports(vararg items: CredentialItem): Boolean {
    for (item in items) {
      if (item is CredentialItem.Password || item is CredentialItem.Username || item is CredentialItem.StringType || item is CredentialItem.YesNoType) {
        continue
      }
      return false
    }
    return true
  }

  override fun get(uri: URIish, vararg items: CredentialItem): Boolean {
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
      else if (item is CredentialItem.YesNoType) {
        UIUtil.invokeAndWaitIfNeeded(Runnable {
          item.setValue(MessageDialogBuilder.yesNo("", item.getPromptText()!!).show() == Messages.YES)
        })
        return true
      }
    }

    if (userNameItem == null && passwordItem == null) {
      return false
    }
    return doGet(uri, userNameItem, passwordItem, sshKeyFile)
  }

  private fun doGet(uri: URIish, userNameItem: CredentialItem.Username?, passwordItem: CredentialItem?, sshKeyFile: String?): Boolean {
    var credentials: Credentials?

    // SSH URL git@github.com:develar/_idea_settings.git, so, username will be "git", we ignore it because in case of SSH credentials account name equals to key filename, but not to username
    val userFromUri: String? = if (sshKeyFile == null) uri.getUser().nullize() else null
    val passwordFromUri: String? = uri.getPass().nullize()
    var saveCredentialsToStore = false
    if (userFromUri != null && passwordFromUri != null) {
      credentials = Credentials(userFromUri, passwordFromUri)
    }
    else {
      // we open password protected SSH key file using OS X keychain - "git credentials" is pointless in this case
      if (sshKeyFile == null || !isOSXCredentialsStoreSupported) {
        if (credentialsFromGit == null) {
          credentialsFromGit = getCredentialsUsingGit(uri, repository)
        }
        credentials = credentialsFromGit
      }
      else {
        credentials = null
      }

      if (credentials == null) {
        try {
          credentials = credentialsStore.getValue().get(uri.getHost(), sshKeyFile)
        }
        catch (e: Throwable) {
          LOG.error(e)
        }

        saveCredentialsToStore = true

        if (userFromUri != null) {
          // username is in url - read password only if it is for the same user
          if (userFromUri != credentials?.id) {
            credentials = Credentials(userFromUri, passwordFromUri)
          }
          else if (passwordFromUri != null && passwordFromUri != credentials?.token) {
            credentials = Credentials(userFromUri, passwordFromUri)
          }
        }
      }
    }

    if (!credentials.isFulfilled()) {
      credentials = showAuthenticationForm(credentials, uri.toStringWithoutCredentials(), uri.getHost(), uri.getPath(), sshKeyFile)
    }

    if (saveCredentialsToStore && credentials.isFulfilled()) {
      credentialsStore.getValue().save(uri.getHost(), credentials!!, sshKeyFile)
    }

    userNameItem?.setValue(credentials?.id)
    if (passwordItem != null) {
      if (passwordItem is CredentialItem.Password) {
        passwordItem.setValue(credentials?.token?.toCharArray())
      }
      else {
        (passwordItem as CredentialItem.StringType).setValue(credentials?.token)
      }
    }

    return credentials != null
  }

  override fun reset(uri: URIish) {
    credentialsFromGit = null
    credentialsStore.getValue().reset(uri.getHost()!!)
  }
}

fun URIish.toStringWithoutCredentials(): String {
  val r = StringBuilder()
  if (getScheme() != null) {
    r.append(getScheme())
    r.append("://")
  }

  if (getHost() != null) {
    r.append(getHost())
    if (getScheme() != null && getPort() > 0) {
      r.append(':')
      r.append(getPort())
    }
  }

  if (getPath() != null) {
    if (getScheme() != null) {
      if (!getPath()!!.startsWith("/")) {
        r.append('/')
      }
    }
    else if (getHost() != null) {
      r.append(':')
    }

    r.append(if (getScheme() != null) getRawPath() else getPath())
  }
  return r.toString()
}
