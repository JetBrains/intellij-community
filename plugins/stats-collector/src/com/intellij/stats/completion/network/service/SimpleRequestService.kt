// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.service

import com.google.common.net.HttpHeaders
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.stats.completion.logger.LineStorage
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLConnection

class SimpleRequestService : RequestService() {
  private companion object {
    val LOG = logger<SimpleRequestService>()
  }

  override fun postZipped(url: String, file: File): ResponseData? {
    return try {
      val zippedArray = LineStorage.readAsZipArray(file)
      return HttpRequests.post(url, "application/json").tuner {
        it.setRequestProperty(HttpHeaders.CONTENT_ENCODING, "gzip")
        it.setRequestProperty(HttpHeaders.CONTENT_LENGTH, zippedArray.size.toString())
        it.setRequestProperty(HttpHeaders.USER_AGENT, ApplicationInfo.getInstance().fullApplicationName)
      }.connect { request ->
        request.write(zippedArray)
        return@connect request.connection.asResponseData(zippedArray.size)
      }
    }
    catch (e: HttpRequests.HttpStatusException) {
      ResponseData(e.statusCode, (e.message ?: ""))
    }
    catch (e: IOException) {
      LOG.debug(e)
      null
    }
  }

  override fun get(url: String): ResponseData? {
    return try {
      val requestBuilder = HttpRequests.request(url)
      return requestBuilder.connect { request ->
        val responseCode = request.getResponseCode()
        val responseText = request.readString()
        ResponseData(responseCode, responseText)
      }
    }
    catch (e: IOException) {
      LOG.debug(e)
      null
    }
  }

  private fun URLConnection.asResponseData(sentDataSize: Int?): ResponseData? {
    if (this is HttpURLConnection) {
      return ResponseData(this.responseCode, StringUtil.notNullize(this.responseMessage, ""), sentDataSize)
    }

    LOG.error("Could not get code and message from http response")
    return null
  }

  private fun HttpRequests.Request.getResponseCode(): Int {
    val connection = this.connection
    if (connection is HttpURLConnection) {
      return connection.responseCode
    }
    // for diagnostic purpose
    if (connection.url.protocol == URLUtil.FILE_PROTOCOL) {
      return 200
    }

    LOG.error("Could not get code from http response")
    return -1
  }
}

data class ResponseData(val code: Int, val text: String = "", val sentDataSize: Int? = null) {
  fun isOK(): Boolean = code in 200..299
}
