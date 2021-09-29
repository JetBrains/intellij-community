// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.collaboration.auth.services.OAuthServiceWithRefresh
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import org.intellij.plugins.markdown.google.utils.GoogleCredentialUtils

internal class GoogleRefreshTokenRequest(
  googleAppCred: GoogleCredentialUtils.GoogleAppCredentials,
  override val refreshToken: String
) : OAuthServiceWithRefresh.RefreshTokenRequest {

  override val refreshTokenUrlWithParameters: Url = TOKEN_URI.addParameters(mapOf(
    "client_id" to googleAppCred.clientId,
    "client_secret" to googleAppCred.clientSecret,
    "refresh_token" to refreshToken,
    "grant_type" to refreshGrantType
  ))

  companion object {
    private const val refreshGrantType = "refresh_token"

    private val TOKEN_URI: Url get() = newFromEncoded("https://oauth2.googleapis.com/token")
  }
}