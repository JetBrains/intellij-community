// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.intellij.openapi.components.Service
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
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

  suspend fun acquireUserInfo(accessToken: String): GoogleUserInfo {
    val response = requestUserInfo(accessToken).asDeferred().await()
    if (response.statusCode() == 200) {
      return deserializeResponse(response)
    }
    else {
      val responseTree = withContext(Dispatchers.IO) { GoogleAccountsUtils.jacksonMapper.readTree(response.body()) }
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
  private fun requestUserInfo(accessToken: String): CompletableFuture<HttpResponse<String>> {
    val userInfoUrl = getUserInfoUrl(accessToken).toExternalForm()
    val client = HttpClient
      .newBuilder()
      .executor(Dispatchers.IO.asExecutor())
      .version(HttpClient.Version.HTTP_1_1)
      .build()
    val httpRequest: HttpRequest = HttpRequest.newBuilder()
      .uri(URI.create(userInfoUrl))
      .header("Content-Type", "application/json")
      .GET()
      .build()

    return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
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
