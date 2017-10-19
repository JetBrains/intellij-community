// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth

import com.intellij.credentialStore.Credentials

class CertificateAuthenticationData(val certificate: Credentials, isStorageAllowed: Boolean) : AuthenticationData(isStorageAllowed) {
  constructor(certificatePath: String, certificatePassword: CharArray?, isStorageAllowed: Boolean) : this(
    Credentials(certificatePath, certificatePassword), isStorageAllowed)

  val certificatePath = certificate.userName.orEmpty()
  val certificatePassword = certificate.getPasswordAsString().orEmpty()
}