package com.intellij.searchEverywhereMl.semantics.utils

import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import java.net.HttpURLConnection
import java.net.URL

internal fun sendRequest(url: String, requestBody: String): RequestResult {
  val parsedUrl = URL(url).toString()
  return try {
    val result = HttpRequests.post(parsedUrl, "application/json; charset=UTF-8").tuner {
      it.setRequestProperty("Accept", "application/json")
      it.doInput = true
      it.doOutput = true
    }.connect { request ->
      request.write(requestBody)
      val responseCode = (request.connection as HttpURLConnection).responseCode
      if (responseCode == HttpURLConnection.HTTP_OK) request.readString() else ""
    }
    RequestResult.Success(result)
  }
  catch (e: HttpStatusException) {
    if (e.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
      InvalidTokenNotificationManager.getInstance().showNotification()
    }
    RequestResult.Error(e.message)
  }
  catch (e: Exception) {
    RequestResult.Error(e.message)
  }
}

sealed interface RequestResult {
  class Success(val data: String) : RequestResult
  class Error(val message: String?) : RequestResult
}