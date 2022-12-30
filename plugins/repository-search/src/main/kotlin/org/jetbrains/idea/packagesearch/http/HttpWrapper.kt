/*
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.packagesearch.http

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.future.await
import org.jetbrains.idea.reposearch.DependencySearchBundle
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class HttpWrapper {
  private val logger = Logger.getInstance(HttpWrapper::class.java)
  private val cache = ConcurrentHashMap<String, String>()

  internal suspend fun requestString(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int = 10,
    headers: List<Pair<String, String>>,
    useCache: Boolean = false,
    verbose: Boolean = true
  ): String {
    val cacheKey = getCacheKey(url, acceptContentType, timeoutInSeconds, headers)
    if (useCache) {
      cache[cacheKey]?.let { return it }
    }

    val request = HttpRequest.newBuilder()
      .uri(URI(url))
      .timeout(Duration.ofSeconds(timeoutInSeconds.toLong()))
      .header("User-Agent", productNameAsUserAgent())
      .header("Accept", acceptContentType)
      .also { builder ->
        headers.forEach { builder.header(it.first, it.second) }
      }
      .GET()
      .build()

    val client = HttpClient.newBuilder()
        .executor(ProcessIOExecutorService.INSTANCE)
        .build()
    val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()
    val responseText = response.body().use { it.readBytes().toString(Charsets.UTF_8) }

    if (response.statusCode() != HttpURLConnection.HTTP_OK && verbose) {
      logger.trace(
        """
          |
          |<-- HTTP GET $url
          |    Accept: $acceptContentType
          |${headers.joinToString("\n") { "    ${it.first}: ${it.second}" }}
          |
          |--> RESPONSE HTTP ${response.statusCode()}
          |$responseText
          |
        """.trimMargin()
      )
    }

    when {
      responseText.isEmpty() -> throw EmptyBodyException()
      else -> return responseText.also { if (useCache) cache[cacheKey] = responseText }
    }
  }

  private fun productNameAsUserAgent(): String {
    val app = ApplicationManager.getApplication()
    return if (app != null && !app.isDisposed) {
      val productName = ApplicationNamesInfo.getInstance().fullProductName
      val version = ApplicationInfo.getInstance().build.asStringWithoutProductCode()
      "$productName/$version"
    }
    else {
      "IntelliJ"
    }
  }

  private fun getCacheKey(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int,
    headers: List<Pair<String, String>>
  ) = (listOf(url, acceptContentType, timeoutInSeconds) + headers.map { it.toString() }).joinToString(":")
}

internal class EmptyBodyException : RuntimeException(
  DependencySearchBundle.message("reposearch.search.client.response.body.is.empty")
)