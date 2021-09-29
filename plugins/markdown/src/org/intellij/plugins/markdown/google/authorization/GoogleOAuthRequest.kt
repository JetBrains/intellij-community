// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.collaboration.auth.services.OAuthPKCERequestBase
import com.intellij.openapi.components.service
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.DigestUtil
import org.intellij.plugins.markdown.google.utils.GoogleCredentialUtils
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService

internal class GoogleOAuthRequest(
  private val googleAppCred: GoogleCredentialUtils.GoogleAppCredentials
) : OAuthPKCERequestBase() {
  private val port: Int get() = BuiltInServerManager.getInstance().port

  override val authorizationCodeUrl: Url
    get() = Urls.newFromEncoded("http://localhost:${port}/${RestService.PREFIX}/${service<GoogleOAuthService>().name}/authorization_code")

  override fun getAuthUrlWithParameters(): Url = AUTHORIZE_URI.addParameters(mapOf(
    "scope" to scope,
    "response_type" to responseType,
    "code_challenge" to generateCodeChallenge(true),
    "code_challenge_method" to codeChallengeMethod,
    "state" to state,
    "client_id" to googleAppCred.clientId,
    "redirect_uri" to authorizationCodeUrl.toExternalForm()
  ))

  override fun getTokenUrlWithParameters(code: String): Url = TOKEN_URI.addParameters(mapOf(
    "client_id" to googleAppCred.clientId,
    "client_secret" to googleAppCred.clientSecret,
    "code" to code,
    "code_verifier" to codeVerifier,
    "grant_type" to authGrantType,
    "redirect_uri" to authorizationCodeUrl.toExternalForm()
  ))

  companion object {
    private val scope
      get() = listOf(
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/userinfo.profile"
      ).joinToString(" ")

    private const val authGrantType = "authorization_code"
    private const val responseType = "code" // For installed applications the parameter value is code
    private const val codeChallengeMethod = "S256"

    private val AUTHORIZE_URI: Url get() = Urls.newFromEncoded("https://accounts.google.com/o/oauth2/v2/auth")
    private val TOKEN_URI: Url get() = Urls.newFromEncoded("https://oauth2.googleapis.com/token")

    private val state: String get() = DigestUtil.randomToken() // state token to prevent request forgery
  }
}