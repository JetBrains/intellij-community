// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirerHttp
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.collaboration.auth.services.OAuthServiceWithRefresh
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.apache.commons.lang.time.DateUtils
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils
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

  // TODO: fix case when some updateAccessToken are started or auth flow is started before
  override fun updateAccessToken(refreshTokenRequest: OAuthServiceWithRefresh.RefreshTokenRequest): CompletableFuture<GoogleCredentials> =
    ProgressManager.getInstance().submitIOTask(EmptyProgressIndicator()) {
      val response = OAuthCredentialsAcquirerHttp.requestToken(refreshTokenRequest.refreshTokenUrlWithParameters)
      val responseDateTime = getLocalDateTime(response.headers().firstValue("date").get())

      if (response.statusCode() == 200) {
        val responseData = with(GoogleAccountsUtils.jacksonMapper) {
          propertyNamingStrategy = PropertyNamingStrategies.SnakeCaseStrategy()
          readValue(response.body(), RefreshResponseData::class.java)
        }

        GoogleCredentials(
          responseData.accessToken,
          refreshTokenRequest.refreshToken,
          responseData.expiresIn,
          responseData.tokenType,
          responseData.scope,
          DateUtils.addSeconds(responseDateTime, responseData.expiresIn.toInt())
        )
      }
      else {
        throw RuntimeException(response.body().ifEmpty { "No token provided" })
      }
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
