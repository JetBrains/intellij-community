// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth

import com.intellij.credentialStore.Credentials

class PasswordAuthenticationData(val credentials: Credentials, isStorageAllowed: Boolean) : AuthenticationData(isStorageAllowed) {
  constructor(userName: String, password: String, isStorageAllowed: Boolean) : this(Credentials(userName, password), isStorageAllowed)

  val userName = credentials.userName.orEmpty()
  val password = credentials.getPasswordAsString().orEmpty()
}