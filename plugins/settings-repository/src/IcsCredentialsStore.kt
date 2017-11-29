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
package org.jetbrains.settingsRepository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Note - in case of SSH, our added password will not be used until ssh-agent will not be restarted (simply execute "killall ssh-agent").
 * Also, if you remove password from keychain, ssh-agent will continue to use cached password.
 */
class IcsCredentialsStore() {
  fun get(host: String?, sshKeyFile: String?, accountName: String?) = CredentialAttributes(host, sshKeyFile, accountName)?.let { PasswordSafe.getInstance().get(it) }

  fun set(host: String?, sshKeyFile: String?, credentials: Credentials?) {
    CredentialAttributes(host, sshKeyFile, credentials?.userName)?.let { PasswordSafe.getInstance().set(it, credentials) }
  }
}

private fun CredentialAttributes(host: String?, sshKeyFile: String?, accountName: String?): CredentialAttributes? {
  if (sshKeyFile == null) {
    return CredentialAttributes(generateServiceName("Settings Repository", host.toString()), accountName)
  }
  else {
    return CredentialAttributes("SSH", sshKeyFile)
  }
}