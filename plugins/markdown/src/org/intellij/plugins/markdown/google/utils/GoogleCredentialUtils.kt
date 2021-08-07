// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.MarkdownNotifier.notifyNetworkProblems
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService
import java.io.IOException
import java.net.URL

object GoogleCredentialUtils {

  data class GoogleAppCredentials(val clientId: String, val clientSecret: String)

  private val LOG = logger<GoogleOAuthService>()

  /**
   * Credentials for Installed application from google console.
   * Note: In this context, the client secret is obviously not treated as a secret.
   * These credentials are fine to be public: https://developers.google.com/identity/protocols/oauth2#installed
   */
  fun getGoogleAppCredentials(project: Project): GoogleAppCredentials? {
    val googleAppCredUrl = "https://www.jetbrains.com/config/markdown.json"

    try {
      val credentials = ObjectMapper().readTree(URL(googleAppCredUrl)).get("google").get("auth")
      val clientId = credentials.get("secret-id").asText()
      val clientSecret = credentials.get("client-secret").asText()

      return GoogleAppCredentials(clientId, clientSecret)
    }
    catch (e: IOException) {
      notifyNetworkProblems(project)
      LOG.error("Can't get google app credentials from https://www.jetbrains.com/config/markdown.json", e)

      return null
    }
  }

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
