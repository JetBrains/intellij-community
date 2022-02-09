// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.collaboration.auth.credentials.CredentialsWithRefresh
import java.util.*

class GoogleCredentials(
  override val accessToken: String,
  override val refreshToken: String,
  override val expiresIn: Long,
  val tokenType: String,
  val scope: String,
  val expirationTime: Date) : CredentialsWithRefresh {

  /**
   * @return true if the token has not expired yet;
   *         false if the token has already expired.
   */
  override fun isAccessTokenValid(): Boolean = Date(System.currentTimeMillis()).before(expirationTime)
}
