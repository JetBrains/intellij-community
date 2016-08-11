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
package org.jetbrains.keychain

import com.intellij.credentialStore.macOs.deleteGenericPassword
import com.intellij.credentialStore.macOs.findGenericPassword
import com.intellij.credentialStore.macOs.saveGenericPassword
import com.intellij.openapi.util.PasswordUtil
import gnu.trove.THashMap

class OsXCredentialsStore(serviceName: String) : CredentialsStore {
  private val serviceName = serviceName.toByteArray()

  companion object {
    val SSH = "SSH".toByteArray()
  }

  private val accountToCredentials = THashMap<String, Credentials>()

  override fun get(host: String?, sshKeyFile: String?): Credentials? {
    if (host == null) {
      return null
    }

    val accountName: String = sshKeyFile ?: host
    var credentials = accountToCredentials.get(accountName)
    if (credentials != null) {
      return credentials
    }

    val data = findGenericPassword(getServiceName(sshKeyFile), accountName) ?: return null
    if (sshKeyFile == null) {
      val separatorIndex = data.indexOf('@')
      if (separatorIndex > 0) {
        val username = PasswordUtil.decodePassword(data.substring(0, separatorIndex))
        val password = PasswordUtil.decodePassword(data.substring(separatorIndex + 1))
        credentials = Credentials(username, password)
      }
      else {
        return null
      }
    }
    else {
      credentials = Credentials(sshKeyFile, data)
    }

    accountToCredentials[accountName] = credentials
    return credentials
  }

  private fun getServiceName(sshKeyFile: String?) = if (sshKeyFile == null) serviceName else SSH

  /**
   * Note - in case of SSH, our added password will not be used until ssh-agent will not be restarted (simply execute "killall ssh-agent").
   * Also, if you remove password from keychain, ssh-agent will continue to use cached password.
   */
  override fun save(host: String?, credentials: Credentials, sshKeyFile: String?) {
    val accountName: String = sshKeyFile ?: host!!
    val oldCredentials = accountToCredentials.put(accountName, credentials)
    if (credentials == oldCredentials) {
      return
    }

    val data = if (sshKeyFile == null) "${PasswordUtil.encodePassword(credentials.id)}@${PasswordUtil.encodePassword(credentials.token)}" else credentials.token!!
    saveGenericPassword(getServiceName(sshKeyFile), accountName, data.toByteArray())
  }

  override fun reset(host: String) {
    if (accountToCredentials.remove(host) != null) {
      deleteGenericPassword(serviceName, host)
    }
  }
}
