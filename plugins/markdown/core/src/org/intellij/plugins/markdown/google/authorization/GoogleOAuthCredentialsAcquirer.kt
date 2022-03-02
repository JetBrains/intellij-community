// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirerHttp
import com.intellij.util.Url
import com.intellij.util.Urls
import org.apache.commons.lang.time.DateUtils
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService.Companion.getLocalDateTime
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService.Companion.jacksonMapper
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils
import java.net.http.HttpHeaders

internal class GoogleOAuthCredentialsAcquirer(
  private val googleAppCred: GoogleAccountsUtils.GoogleAppCredentials,
  private val authorizationCodeUrl: Url,
  private val codeVerifier: String
) : OAuthCredentialsAcquirer<GoogleCredentials> {

  override fun acquireCredentials(code: String): OAuthCredentialsAcquirer.AcquireCredentialsResult<GoogleCredentials> {
    return OAuthCredentialsAcquirerHttp.requestToken(getTokenUrlWithParameters(code)) { body, headers ->
      getCredentials(body, headers)
    }
  }

  private fun getTokenUrlWithParameters(code: String): Url = TOKEN_URI.addParameters(mapOf(
    "client_id" to googleAppCred.clientId,
    "client_secret" to googleAppCred.clientSecret,
    "code" to code,
    "code_verifier" to codeVerifier,
    "grant_type" to authGrantType,
    "redirect_uri" to authorizationCodeUrl.toExternalForm()
  ))

  private fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): GoogleCredentials {
    val responseDateTime = getLocalDateTime(responseHeaders.firstValue("date").get())
    val responseData = with(jacksonMapper) {
      propertyNamingStrategy = PropertyNamingStrategies.SnakeCaseStrategy()
      readValue(responseBody, AuthorizationResponseData::class.java)
    }

    return GoogleCredentials(
      responseData.accessToken,
      responseData.refreshToken,
      responseData.expiresIn,
      responseData.tokenType,
      responseData.scope,
      DateUtils.addSeconds(responseDateTime, responseData.expiresIn.toInt())
    )
  }

  private data class AuthorizationResponseData(val accessToken: String,
                                               val refreshToken: String,
                                               val expiresIn: Long,
                                               val tokenType: String,
                                               val scope: String,
                                               val idToken: String)

  companion object {
    private const val authGrantType = "authorization_code"

    private val TOKEN_URI: Url get() = Urls.newFromEncoded("https://oauth2.googleapis.com/token")
  }
}
