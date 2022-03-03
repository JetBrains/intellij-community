// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.google.GoogleAuthorizedUserException
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserInfo
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

@Service
internal class GoogleUserInfoService {

  companion object {
    private val USER_INFO_URL: Url get() = Urls.newFromEncoded("https://www.googleapis.com/oauth2/v2/userinfo")
  }

  @RequiresEdt
  fun acquireUserInfo(accessToken: String, progressIndicator: ProgressIndicator): CompletableFuture<GoogleUserInfo> =
    ProgressManager.getInstance().submitIOTask(progressIndicator) {
      val response = requestUserInfo(accessToken)

      if (response.statusCode() == 200) {
        return@submitIOTask deserializeResponse(response)
      }
      else {
        val responseTree = GoogleAccountsUtils.jacksonMapper.readTree(response.body())
        if (responseTree.isEmpty) {
          throw RuntimeException("Couldn't get user data")
        }
        else {
          when (responseTree.get("error").get("code").asInt()) {
            401 -> throw GoogleAuthorizedUserException()
            else -> throw RuntimeException(responseTree.get("error").get("status").asText())
          }
        }
      }
    }

  @RequiresBackgroundThread
  private fun requestUserInfo(accessToken: String): HttpResponse<String> {
    val userInfoUrl = getUserInfoUrl(accessToken).toExternalForm()
    val client = HttpClient.newHttpClient()
    val httpRequest: HttpRequest = HttpRequest.newBuilder()
      .uri(URI.create(userInfoUrl))
      .header("Content-Type", "application/json")
      .GET()
      .build()

    return client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
  }

  @RequiresBackgroundThread
  private fun deserializeResponse(response: HttpResponse<String>) =
    with(GoogleAccountsUtils.jacksonMapper) {
      propertyNamingStrategy = PropertyNamingStrategies.SnakeCaseStrategy()
      readValue(response.body(), GoogleUserInfo::class.java)
    }

  private fun getUserInfoUrl(accessToken: String) = USER_INFO_URL.addParameters(mapOf(
    "access_token" to accessToken
  ))
}
