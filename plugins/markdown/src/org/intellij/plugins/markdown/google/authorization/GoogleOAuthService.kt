// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirerHttp
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.collaboration.auth.services.OAuthServiceWithRefresh
import com.intellij.openapi.components.Service
import org.apache.commons.lang.time.DateUtils
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class GoogleOAuthService : OAuthServiceBase<GoogleCredentials>(), OAuthServiceWithRefresh<GoogleCredentials> {
  companion object {
    val jacksonMapper: ObjectMapper get() = jacksonObjectMapper()

    fun getLocalDateTime(responseDate: String): Date =
      SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(responseDate)
  }

  override val name: String get() = "google/oauth"

  override fun updateAccessToken(refreshTokenRequest: OAuthServiceWithRefresh.RefreshTokenRequest): CompletableFuture<GoogleCredentials> {
    // TODO: fix case when some updateAccessToken are started or auth flow is started before

    val result = CompletableFuture<GoogleCredentials>()

    result.whenComplete { _, _ -> currentRequest.set(null) }

    try {
      val response = OAuthCredentialsAcquirerHttp.requestToken(refreshTokenRequest.refreshTokenUrlWithParameters)
      val responseDateTime = getLocalDateTime(response.headers().firstValue("date").get())

      if (response.statusCode() == 200) {
        val responseData = with(jacksonMapper) {
          propertyNamingStrategy = PropertyNamingStrategies.SnakeCaseStrategy()
          readValue(response.body(), RefreshResponseData::class.java)
        }
        val creds = GoogleCredentials(
          responseData.accessToken,
          refreshTokenRequest.refreshToken,
          responseData.expiresIn,
          responseData.tokenType,
          responseData.scope,
          DateUtils.addSeconds(responseDateTime, responseData.expiresIn.toInt())
        )

        result.complete(creds)
      }
      else {
        result.completeExceptionally(Exception(response.body().ifEmpty { "No token provided" }))
      }
    }
    catch (e: IOException) {
      result.completeExceptionally(e)
    }

    return result
  }

  override fun revokeToken(token: String) {
    TODO("Not yet implemented")
  }

  private data class RefreshResponseData(val accessToken: String,
                                         val expiresIn: Long,
                                         val scope: String,
                                         val tokenType: String,
                                         val idToken: String)
}
