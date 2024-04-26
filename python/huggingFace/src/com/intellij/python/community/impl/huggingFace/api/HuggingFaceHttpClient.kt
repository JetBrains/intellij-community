// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.net.HttpURLConnection
import java.net.URL

@ApiStatus.Internal data class HfHttpResponseWithHeaders(val content: String?, val linkHeader: String?)

@ApiStatus.Internal
object HuggingFaceHttpClient {
  suspend fun downloadFile(url: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      HttpRequests.request(url).readString()
    }.onFailure {
      thisLogger().warn("Failed to download file: $url", it)
    }
  }

  suspend fun downloadContentAndHeaders(url: String): Result<HfHttpResponseWithHeaders> = withContext(Dispatchers.IO) {
    runCatching {
      val connection = URL(url).openConnection() as HttpURLConnection
      val content = connection.inputStream.bufferedReader().use { it.readText() }
      val linkHeader = connection.getHeaderField("Link")
      connection.disconnect()
      HfHttpResponseWithHeaders(content, linkHeader)
    }.onFailure {
      thisLogger().warn("Failed to download: $url", it)
    }
  }
}
