package com.intellij.ide.starter.utils

import com.intellij.ide.starter.utils.FileSystem.isFileUpToDate
import com.intellij.tools.ide.util.common.NoRetryException
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// TODO: migrate on okhttp ?
object HttpClient {
  private val locks = ConcurrentHashMap<String, ReentrantLock>()

  var credentialsProvider: ((String) -> Pair<String, String>?)? = null

  fun <Y> sendRequest(request: HttpUriRequest, processor: (HttpResponse) -> Y): Y {
    HttpClientBuilder.create().build().use { client ->
      client.execute(request).use { response ->
        return processor(response)
      }
    }
  }

  /**
   * Downloading file from [url] to [outPath] with [retries].
   * @return true - if successful, false - otherwise
   */
  fun download(url: String, outPath: Path, retries: Long = 3, timeout: Duration = 10.minutes) {
    val encodeUrl = url.replace(" ", "%20")
    logOutput("Downloading $encodeUrl to $outPath")

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      withTimeout(timeout = timeout) {
        withRetry(messageOnFailure = "Failure during downloading $encodeUrl to $outPath", retries = retries) {
          val request = HttpGet(encodeUrl)

          val uri = URI(encodeUrl)
          val targetHost = HttpHost(uri.host, uri.port, uri.scheme)
          val context = HttpClientContext.create()

          // Use host-scoped credentials so that auth is NOT forwarded on cross-origin
          // redirects (e.g. packages.jetbrains.team → pre-signed S3 URL).
          val credsProvider = BasicCredentialsProvider()
          credentialsProvider?.invoke(encodeUrl)?.let { (username, password) ->
            credsProvider.setCredentials(AuthScope(targetHost), UsernamePasswordCredentials(username, password))
            // Preemptive auth: send credentials on first request without waiting for 401 challenge
            context.authCache = BasicAuthCache().apply { put(targetHost, BasicScheme()) }
          }

          HttpClientBuilder.create()
            .setDefaultCredentialsProvider(credsProvider)
            .build().use { client ->
              client.execute(request, context).use { response ->
                processDownloadResponse(response, encodeUrl, url, outPath)
              }
            }
        }
      }
    }
  }

  private fun processDownloadResponse(response: HttpResponse, encodeUrl: String, originalUrl: String, outPath: Path) {
    val statusCode = response.statusLine.statusCode

    if (statusCode != 200) {
      val errorBody = try {
        EntityUtils.toString(response.entity, Charsets.UTF_8).take(2000)
      }
      catch (_: Exception) {
        "<could not read body>"
      }
      logOutput("Download failed for $encodeUrl: HTTP $statusCode ${response.statusLine.reasonPhrase}")
      logOutput("  Response headers: ${response.allHeaders.joinToString { "${it.name}: ${it.value}" }}")
      logOutput("  Error response body: $errorBody")
    }

    if (statusCode == 404) {
      throw HttpNotFound("Server returned 404 Not Found: $encodeUrl")
    }

    if (statusCode == 403 && originalUrl.startsWith("https://cache-redirector.jetbrains.com/")) {
      // all downloads from https://cache-redirector.jetbrains.com should be public, but some endpoints return 403 instead of 404
      // due to blocking of files listing on S3 bucket
      throw HttpNotFound("Server returned 403 which we interpret as not found for cache-redirector urls: $originalUrl")
    }

    if (statusCode == 403) throw HttpForbidden("Server returned 403 Forbidden: $encodeUrl")

    check(statusCode == 200) { "Failed to download $originalUrl: HTTP $statusCode ${response.statusLine.reasonPhrase}" }

    if (!outPath.parent.exists()) {
      outPath.parent.createDirectories()
    }
    outPath.deleteIfExists()

    val tempFile = Files.createTempFile(outPath.parent, outPath.name, "-download.tmp")
    try {
      tempFile.outputStream().buffered(10 * 1024 * 1024).use { stream ->
        response.entity?.writeTo(stream)
      }

      // there could a parallel download to the same destination, handle it gracefully (both will succeed)
      tempFile.moveTo(outPath, overwrite = true)
    }
    finally {
      tempFile.deleteIfExists()
    }
  }

  /**
   * [url] - source to download
   * [targetFile] - output file
   * [retries] - how many times retry to download in case of failure
   * @return true - if successful, false - otherwise
   */
  fun downloadIfMissing(url: String, targetFile: Path, retries: Long = 3, timeout: Duration = 10.minutes) {
    getLock(targetFile).withLock {
      if (targetFile.isFileUpToDate()) {
        logOutput("File $targetFile was already downloaded. Size ${targetFile.fileSize().formatSize()}")
        return
      } else targetFile.deleteIfExists()

      return download(url, targetFile, retries, timeout)
    }
  }

  class HttpNotFound(message: String, cause: Throwable? = null) : NoRetryException(message, cause)
  class HttpForbidden(message: String, cause: Throwable? = null) : NoRetryException(message, cause)

  private fun getLock(path: Path): ReentrantLock = locks.getOrPut(path.toAbsolutePath().toString()) { ReentrantLock() }
}
