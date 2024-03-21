// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import java.net.HttpURLConnection
import java.net.URL

@ApiStatus.Internal data class HfHttpResponseWithHeaders(val content: String?, val linkHeader: String?)

@ApiStatus.Internal
object HuggingFaceHttpClient {
  fun exists(url: String, retries: Int = 2): Boolean {
    repeat(retries) {
      if (existsSingle(url))
        return true
    }
    return false
  }

  private fun existsSingle(url: String) = try {
    val code = HttpRequests.request(url).connect {
      (it.connection as HttpURLConnection).responseCode
    }

    code in 200..299
  }
  catch (t: Throwable) {
    thisLogger().warn(t)
    false
  }

  fun downloadFile(url: String): String? {
    return try {
      HttpRequests.request(url).readString()
    } catch (t: Throwable) {
      thisLogger().warn("Failed to download file: $url", t)
      null
    }
  }

  fun downloadContentAndHeaders(url: String): HfHttpResponseWithHeaders {
    return try {
      val connection = URL(url).openConnection() as HttpURLConnection
      val content = connection.inputStream.bufferedReader().use { it.readText() }
      val linkHeader = connection.getHeaderField("Link")
      connection.disconnect()
      HfHttpResponseWithHeaders(content, linkHeader)
    } catch (t: Throwable) {
      thisLogger().warn("Failed to download: $url", t)
      HfHttpResponseWithHeaders(null, null)
    }
  }
}
