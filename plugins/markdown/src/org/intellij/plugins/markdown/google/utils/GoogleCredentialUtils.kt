// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.utils

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials

object GoogleCredentialUtils {

  /**
   * Converts internal GoogleCredentials to Credentials that the Google API can work with
   */
  fun createCredentialsForGoogleApi(credentials: GoogleCredentials): Credential {
    val tokenResponse = getTokenResponse(credentials)

    return GoogleCredential().setFromTokenResponse(tokenResponse)
  }

  /**
   * Converts GoogleCredentials to TokenResponse to further get the Credential needed to work with the Drive API
   */
  private fun getTokenResponse(credentials: GoogleCredentials): TokenResponse = TokenResponse().apply {
    accessToken = credentials.accessToken
    tokenType = credentials.tokenType
    scope = credentials.scope
    expiresInSeconds = credentials.expiresIn
  }
}
