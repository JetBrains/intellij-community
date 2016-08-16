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

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.util.PasswordUtil

class FileCredentialsStore() : CredentialsStore {
  override fun get(host: String?, sshKeyFile: String?): Credentials? {
    if (host == null) {
      return null
    }

    val accountName = sshKeyFile ?: host

    val data = PasswordSafe.getInstance().getPassword("ics-" + accountName) ?: return null
    if (sshKeyFile == null) {
      val separatorIndex = data.indexOf('@')
      if (separatorIndex > 0) {
        val username = PasswordUtil.decodePassword(data.substring(0, separatorIndex))
        val password = PasswordUtil.decodePassword(data.substring(separatorIndex + 1))
        return Credentials(username, password)
      }
    }
    else {
      return Credentials(sshKeyFile, data)
    }

    return null
  }

  override fun reset(host: String) {
    PasswordSafe.getInstance().setPassword("ics-" + host, null)
  }

  override fun save(host: String?, credentials: Credentials, sshKeyFile: String?) {
    val accountName: String = sshKeyFile ?: host!!
    PasswordSafe.getInstance().setPassword("ics-" + accountName, if (sshKeyFile == null) "${PasswordUtil.encodePassword(credentials.id)}@${PasswordUtil.encodePassword(credentials.token)}" else credentials.token!!)
  }
}