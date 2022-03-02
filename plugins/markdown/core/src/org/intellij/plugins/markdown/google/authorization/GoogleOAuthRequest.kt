// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthRequest
import com.intellij.collaboration.auth.services.PkceUtils
import com.intellij.openapi.components.service
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.DigestUtil
import org.intellij.plugins.markdown.google.GoogleAppCredentialsException
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import java.util.*

internal fun getGoogleAuthRequest(): GoogleOAuthRequest {
  val googleAppCred = GoogleAccountsUtils.getGoogleAppCredentials() ?: throw GoogleAppCredentialsException()
  return GoogleOAuthRequest(googleAppCred)
}

internal class GoogleOAuthRequest(googleAppCred: GoogleAccountsUtils.GoogleAppCredentials) : OAuthRequest<GoogleCredentials> {
  private val port: Int get() = BuiltInServerManager.getInstance().port

  private val encoder = Base64.getUrlEncoder().withoutPadding()
  private val codeVerifier = PkceUtils.generateCodeVerifier()
  private val codeChallenge = PkceUtils.generateShaCodeChallenge(codeVerifier, encoder)

  override val authorizationCodeUrl: Url
    get() = Urls.newFromEncoded("http://localhost:${port}/${RestService.PREFIX}/${service<GoogleOAuthService>().name}/authorization_code")

  override val credentialsAcquirer: OAuthCredentialsAcquirer<GoogleCredentials> =
    GoogleOAuthCredentialsAcquirer(googleAppCred, authorizationCodeUrl, codeVerifier)

  override val authUrlWithParameters: Url = AUTHORIZE_URI.addParameters(mapOf(
    "scope" to scope,
    "response_type" to responseType,
    "code_challenge" to codeChallenge,
    "code_challenge_method" to codeChallengeMethod,
    "state" to state,
    "client_id" to googleAppCred.clientId,
    "redirect_uri" to authorizationCodeUrl.toExternalForm()
  ))

  companion object {
    private val scope
      get() = listOf(
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/userinfo.profile",
        "https://www.googleapis.com/auth/userinfo.email"
      ).joinToString(" ")

    private const val responseType = "code" // For installed applications the parameter value is code
    private const val codeChallengeMethod = "S256"

    private val AUTHORIZE_URI: Url get() = Urls.newFromEncoded("https://accounts.google.com/o/oauth2/v2/auth")

    private val state: String get() = DigestUtil.randomToken() // state token to prevent request forgery
  }
}
