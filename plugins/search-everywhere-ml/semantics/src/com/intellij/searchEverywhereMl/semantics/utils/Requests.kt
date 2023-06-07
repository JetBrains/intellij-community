package com.intellij.searchEverywhereMl.semantics.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection
import java.net.URL

private val LOG = Logger.getInstance("SearchEverywhereSemanticRequests")

internal fun sendRequest(url: String, requestBody: String): String? {
  val parsedUrl = URL(url).toString()
  return try {
    HttpRequests.post(parsedUrl, "application/json; charset=UTF-8").tuner {
      it.setRequestProperty("Accept", "application/json")
      it.doInput = true
      it.doOutput = true
    }.connect { request ->
      request.write(requestBody)
      val responseCode = (request.connection as HttpURLConnection).responseCode
      if (responseCode == HttpURLConnection.HTTP_OK) request.readString() else ""
    }
  }
  catch (e: Exception) {
    LOG.warn(e)
    null
  }
}