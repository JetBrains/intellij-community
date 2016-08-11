/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.git

import com.intellij.credentialStore.macOs.isMacOsCredentialStoreSupported
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
        val promptText = item.promptText
        if (promptText != null) {
          val marker = "Passphrase for "
          if (promptText.startsWith(marker) /* JSch prompt */) {
            sshKeyFile = promptText.substring(marker.length)
            passwordItem = item
            continue
          }
        }
      }
      else if (item is CredentialItem.YesNoType) {
        UIUtil.invokeAndWaitIfNeeded(Runnable {
          item.value = MessageDialogBuilder.yesNo("", item.promptText!!).show() == Messages.YES
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
    val userFromUri: String? = if (sshKeyFile == null) uri.user.nullize() else null
    val passwordFromUri: String? = uri.pass.nullize()
    var saveCredentialsToStore = false
    if (userFromUri != null && passwordFromUri != null) {
      credentials = Credentials(userFromUri, passwordFromUri)
    }
    else {
      // we open password protected SSH key file using OS X keychain - "git credentials" is pointless in this case
      if (sshKeyFile == null || !isMacOsCredentialStoreSupported) {
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
          credentials = credentialsStore.value.get(uri.host, sshKeyFile)
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
      credentials = showAuthenticationForm(credentials, uri.toStringWithoutCredentials(), uri.host, uri.path, sshKeyFile)
    }

    if (saveCredentialsToStore && credentials.isFulfilled()) {
      credentialsStore.value.save(uri.host, credentials!!, sshKeyFile)
    }

    userNameItem?.value = credentials?.id
    if (passwordItem != null) {
      if (passwordItem is CredentialItem.Password) {
        passwordItem.value = credentials?.token?.toCharArray()
      }
      else {
        (passwordItem as CredentialItem.StringType).value = credentials?.token
      }
    }

    return credentials != null
  }

  override fun reset(uri: URIish) {
    credentialsFromGit = null
    credentialsStore.value.reset(uri.host!!)
  }
}

fun URIish.toStringWithoutCredentials(): String {
  val r = StringBuilder()
  if (scheme != null) {
    r.append(scheme)
    r.append("://")
  }

  if (host != null) {
    r.append(host)
    if (scheme != null && port > 0) {
      r.append(':')
      r.append(port)
    }
  }

  if (path != null) {
    if (scheme != null) {
      if (!path!!.startsWith("/")) {
        r.append('/')
      }
    }
    else if (host != null) {
      r.append(':')
    }

    r.append(if (scheme != null) rawPath else path)
  }
  return r.toString()
}
