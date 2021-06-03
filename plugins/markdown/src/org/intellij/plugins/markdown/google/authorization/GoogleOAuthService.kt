// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.collaboration.auth.services.OAuthServiceWithRefresh
import com.intellij.openapi.components.Service
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.DigestUtil.randomToken
import org.apache.commons.lang.time.DateUtils
import org.intellij.plugins.markdown.google.utils.GoogleCredentialUtils
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import java.io.IOException
import java.net.http.HttpHeaders
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class GoogleOAuthService : OAuthServiceBase<GoogleCredentials>(), OAuthServiceWithRefresh<GoogleCredentials> {
  companion object {
    private val scope
      get() = listOf(
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/userinfo.profile"
      ).joinToString(" ")

    private const val authGrantType = "authorization_code"
    private const val refreshGrantType = "refresh_token"
    private const val responseType = "code" // For installed applications the parameter value is code
    private const val codeChallengeMethod = "S256"

    private val port: Int get() = BuiltInServerManager.getInstance().port
    private val state: String get() = randomToken() // state token to prevent request forgery
    private val codeVerifier: String = randomToken()
    private val codeChallenge: String
      get() = Base64.getUrlEncoder().withoutPadding().encodeToString(DigestUtil.sha256().digest(codeVerifier.toByteArray()))

    private val AUTHORIZE_URI: Url get() = newFromEncoded("https://accounts.google.com/o/oauth2/v2/auth")
    private val TOKEN_URI: Url get() = newFromEncoded("https://oauth2.googleapis.com/token")

    private val jacksonMapper: ObjectMapper get() = jacksonObjectMapper()
  }

  var googleAppCred: GoogleCredentialUtils.GoogleAppCredentials? = null

  override val name: String get() = "google/oauth"
  override val authorizationCodeUrl: Url get() = newFromEncoded("http://localhost:$port/${RestService.PREFIX}/$name/authorization_code")

  //TODO: Maybe fix them to something like https://account.jetbrains.com/[SERVICE NAME]/intellij/[...] or create these pages somewhere? 🤔
  // note: so far, these addresses are 404
  override val successRedirectUrl: Url get() = newFromEncoded("http://localhost:$port/success")
  override val errorRedirectUrl: Url get() = newFromEncoded("http://localhost:$port/error")

  override fun updateAccessToken(refreshToken: String): CompletableFuture<GoogleCredentials> {
    if (!currentRequest.compareAndSet(null, CompletableFuture())) {
      return currentRequest.get()!!
    }

    val request = currentRequest.get()!!
    request.whenComplete { _, _ -> currentRequest.set(null) }

    try {
      val refreshTokenUrl = getRefreshTokenUrlWithParameters(refreshToken).toExternalForm()
      val response = postHttpResponse(refreshTokenUrl)
      val responseDateTime = getLocalDateTime(response.headers().firstValue("date").get())

      if (response.statusCode() == 200) {
        val responseData = with(jacksonMapper) {
          propertyNamingStrategy = PropertyNamingStrategies.SnakeCaseStrategy()
          readValue(response.body(), RefreshResponseData::class.java)
        }
        val result = GoogleCredentials(
          responseData.accessToken,
          refreshToken,
          responseData.expiresIn,
          responseData.tokenType,
          responseData.scope,
          DateUtils.addSeconds(responseDateTime, responseData.expiresIn.toInt())
        )

        request.complete(result)
      }
      else {
        request.completeExceptionally(Exception(response.body().ifEmpty { "No token provided" }))
      }
    }
    catch (e: IOException) {
      request.completeExceptionally(e)
    }

    return request
  }

  override fun revokeToken(token: String) {
    TODO("Not yet implemented")
  }

  override fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): GoogleCredentials {
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

  override fun getAuthUrlWithParameters(): Url = AUTHORIZE_URI.addParameters(mapOf(
    "scope" to scope,
    "response_type" to responseType,
    "code_challenge" to codeChallenge,
    "code_challenge_method" to codeChallengeMethod,
    "state" to state,
    "client_id" to googleAppCred?.clientId,
    "redirect_uri" to authorizationCodeUrl.toExternalForm()
  ))

  override fun getTokenUrlWithParameters(code: String): Url = TOKEN_URI.addParameters(mapOf(
    "client_id" to googleAppCred?.clientId,
    "client_secret" to googleAppCred?.clientSecret,
    "redirect_uri" to authorizationCodeUrl.toExternalForm(),
    "code" to code,
    "code_verifier" to codeVerifier,
    "grant_type" to authGrantType
  ))

  private fun getRefreshTokenUrlWithParameters(refreshToken: String): Url = TOKEN_URI.addParameters(mapOf(
    "client_id" to  googleAppCred?.clientId,
    "client_secret" to googleAppCred?.clientSecret,
    "refresh_token" to refreshToken,
    "grant_type" to refreshGrantType
  ))

  private fun getLocalDateTime(responseDate: String) =
    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(responseDate)

  private data class AuthorizationResponseData(val accessToken: String,
                                               val refreshToken: String,
                                               val expiresIn: Long,
                                               val tokenType: String,
                                               val scope: String,
                                               val idToken: String)

  private data class RefreshResponseData(val accessToken: String,
                                         val expiresIn: Long,
                                         val scope: String,
                                         val tokenType: String,
                                         val idToken: String)
}
