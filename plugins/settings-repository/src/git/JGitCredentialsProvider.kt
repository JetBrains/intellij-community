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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.isFulfilled
import com.intellij.credentialStore.isMacOsCredentialStoreSupported
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.util.text.nullize
import com.intellij.util.ui.UIUtil
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.jetbrains.settingsRepository.IcsCredentialsStore
import org.jetbrains.settingsRepository.catchAndLog
import org.jetbrains.settingsRepository.showAuthenticationForm
import java.util.concurrent.TimeUnit

class JGitCredentialsProvider(private val credentialsStore: Lazy<IcsCredentialsStore>, private val repository: Repository) : CredentialsProvider() {
  private val credentialsFromGit = CacheBuilder.newBuilder()
      .expireAfterAccess(5, TimeUnit.MINUTES)
      .build(object : CacheLoader<URIish, Credentials>() {
        override fun load(it: URIish) = getCredentialsUsingGit(it, repository) ?: Credentials(null)
      })

  override fun isInteractive(): Boolean = true

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
    var credentials: Credentials? = null

    // SSH URL git@github.com:develar/_idea_settings.git, so, username will be "git", we ignore it because in case of SSH credentials account name equals to key filename, but not to username
    val userFromUri: String? = if (sshKeyFile == null) uri.user.nullize() else null
    val passwordFromUri: String? = uri.pass.nullize()
    if (userFromUri != null && passwordFromUri != null) {
      credentials = Credentials(userFromUri, passwordFromUri)
    }
    else {
      catchAndLog {
        credentials = credentialsStore.value.get(uri.host, sshKeyFile, userFromUri)
        // we open password protected SSH key file using OS X keychain - "git credentials" is pointless in this case
        if (!credentials.isFulfilled() && (sshKeyFile == null || !isMacOsCredentialStoreSupported)) {
          credentials = credentialsFromGit.get(uri)
        }
      }
    }

    if (!credentials.isFulfilled()) {
      credentials = showAuthenticationForm(credentials, uri.toStringWithoutCredentials(), uri.host, uri.path, sshKeyFile)
      if (credentials.isFulfilled()) {
        credentialsStore.value.set(uri.host, sshKeyFile, credentials)
      }
    }

    userNameItem?.value = credentials?.userName
    if (passwordItem != null) {
      if (passwordItem is CredentialItem.Password) {
        passwordItem.value = credentials?.password?.toCharArray()
      }
      else {
        (passwordItem as CredentialItem.StringType).value = credentials?.password?.toString()
      }
    }

    return credentials.isFulfilled()
  }

  override fun reset(uri: URIish) {
    credentialsFromGit.invalidate(uri)
    credentialsFromGit.cleanUp()
    credentialsStore.value.set(uri.host!!, null, null)
  }
}

private fun URIish.toStringWithoutCredentials(): String {
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
