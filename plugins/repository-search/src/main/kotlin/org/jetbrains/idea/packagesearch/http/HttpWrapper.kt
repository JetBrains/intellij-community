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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.castSafelyTo
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.idea.reposearch.DependencySearchBundle
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpWrapper {
  private val cache = HashMap<String, String>()

  internal suspend fun requestString(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int = 10,
    headers: List<Pair<String, String>>,
    useCache: Boolean = false,
    verbose: Boolean = true
  ): String = suspendCancellableCoroutine { cont ->
    try {
      val cacheKey = getCacheKey(url, acceptContentType, timeoutInSeconds, headers)
      if (useCache && cache.containsKey(cacheKey)) {
        cont.resume(cacheKey)
      }

      val builder = HttpRequests.request(url)
        .productNameAsUserAgent()
        .accept(acceptContentType)
        .connectTimeout(timeoutInSeconds * 1000)
        .readTimeout(timeoutInSeconds * 1000)
        .tuner { connection ->
          headers.forEach {
            connection.setRequestProperty(it.first, it.second)
          }
        }
      builder.connect { request ->
        val statusCode = request.connection.castSafelyTo<HttpURLConnection>()?.responseCode ?: -1
        val responseText = request.connection.getInputStream().use { it.readBytes { cont.isCancelled }.toString(Charsets.UTF_8) }
        if (cont.isCancelled) return@connect
        if (statusCode != HttpURLConnection.HTTP_OK && verbose) {
          Logger.getInstance("HttpWrapper").debug(
            """
            |
            |<-- HTTP GET $url
            |    Accept: $acceptContentType
            |${headers.joinToString("\n") { "    ${it.first}: ${it.second}" }}
            |
            |--> RESPONSE HTTP $statusCode
            |$responseText
            |
          """.trimMargin()
          )
        }

        when {
          responseText.isEmpty() -> cont.resumeWithException(EmptyBodyException())
          else -> cont.resume(responseText).also { if (useCache) cache[cacheKey] = responseText }
        }
      }
    }
    catch (t: Throwable) {
      cont.resumeWithException(t)
    }
  }

  private fun getCacheKey(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int,
    headers: List<Pair<String, String>>
  ) = (listOf(url, acceptContentType, timeoutInSeconds) + headers.map { it.toString() }).joinToString(":")

  private fun InputStream.copyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, cancellationRequested: () -> Boolean): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0 && !cancellationRequested()) {
      out.write(buffer, 0, bytes)
      bytesCopied += bytes
      bytes = read(buffer)
    }
    return bytesCopied
  }

  private fun InputStream.readBytes(cancellationRequested: () -> Boolean): ByteArray {
    val buffer = ByteArrayOutputStream(maxOf(DEFAULT_BUFFER_SIZE, this.available()))
    copyTo(buffer, cancellationRequested = cancellationRequested)
    return buffer.toByteArray()
  }
}

internal class EmptyBodyException : RuntimeException(
  DependencySearchBundle.message("reposearch.search.client.response.body.is.empty")
)