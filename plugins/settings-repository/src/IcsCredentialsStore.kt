// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Note - in case of SSH, our added password will not be used until ssh-agent will not be restarted (simply execute "killall ssh-agent").
 * Also, if you remove password from keychain, ssh-agent will continue to use cached password.
 */
class IcsCredentialsStore {
  fun get(host: String?, sshKeyFile: String?, accountName: String?) = CredentialAttributes(host, sshKeyFile, accountName)?.let { PasswordSafe.instance.get(it) }

  fun set(host: String?, sshKeyFile: String?, credentials: Credentials?) {
    CredentialAttributes(host, sshKeyFile, credentials?.userName)?.let { PasswordSafe.instance.set(it, credentials) }
  }
}

@Suppress("FunctionName")
private fun CredentialAttributes(host: String?, sshKeyFile: String?, accountName: String?): CredentialAttributes? {
  if (sshKeyFile == null) {
    return CredentialAttributes(generateServiceName("Settings Repository", host.toString()), accountName)
  }
  else {
    return CredentialAttributes("SSH", sshKeyFile)
  }
}