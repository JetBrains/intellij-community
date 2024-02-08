// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceLastCacheRefresh
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceSafeExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser



object HuggingFaceApi {
  fun fillCacheWithBasicApiData(endpoint: HuggingFaceEntityKind, cache: HuggingFaceCache, maxCount: Int, project: Project) {
    val refreshState = project.getService(HuggingFaceLastCacheRefresh::class.java)

    val startUrl = HuggingFaceURLProvider.fetchApiDataUrl(endpoint)
    val executor = HuggingFaceSafeExecutor.instance

    val currentTime = System.currentTimeMillis()
    if (currentTime - refreshState.lastRefreshTime < Duration.ofDays(1).toMillis()) {
      return
    }

    executor.asyncSuspend("FetchHuggingFace$endpoint") {
      var nextPageUrl: URL? = startUrl
      while (nextPageUrl != null && cache.getCacheSize() < maxCount) {
        val urlConnection = nextPageUrl.openConnection() as HttpURLConnection
        val dataMap = fetchBasicDataFromApi(endpoint, nextPageUrl.toString())
        cache.saveEntities(dataMap)
        nextPageUrl = getNextPageUrl(urlConnection)
        urlConnection.disconnect()
      }
    }
  }

  private suspend fun fetchBasicDataFromApi(endpointKind: HuggingFaceEntityKind, url: String):
    Map<String, HuggingFaceEntityBasicApiData>
  {
    return withContext(Dispatchers.IO) {
      val urlConnection = URL(url).openConnection() as HttpURLConnection
      urlConnection.requestMethod = "GET"

      try {
        if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
          urlConnection.inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            parseBasicEntityData(endpointKind, response)
          }
        } else {
          emptyMap()
        }
      } finally {
        urlConnection.disconnect()
      }
    }
  }

  private fun getNextPageUrl(connection: HttpURLConnection): URL? {
    val linkHeader = connection.getHeaderField("Link") ?: return null
    val nextLinkMatch = Regex("""<(.+)>; rel="next"""").find(linkHeader)
    return nextLinkMatch?.let { URL(it.groupValues[1]) }
  }

  private fun parseBasicEntityData(
    endpointKind: HuggingFaceEntityKind,
    json: String
  ): Map<String, HuggingFaceEntityBasicApiData> {
    val jsonArray: JsonArray = JsonParser.parseString(json).asJsonArray
    val modelDataMap = mutableMapOf<String, HuggingFaceEntityBasicApiData>()

    jsonArray.forEach { element ->
      val jsonObject: JsonObject = element.asJsonObject
      jsonObject.get("id")?.asString?.let { id ->
        if (id.isNotEmpty()) {
          @NlsSafe val nlsSafeId = id
          @NlsSafe val pipelineTag = jsonObject.get("pipeline_tag")?.asString ?: "unknown"

          val gated = jsonObject.get("gated")?.asString ?: "true"
          val downloads = jsonObject.get("downloads")?.asInt ?: -1
          val likes = jsonObject.get("likes")?.asInt ?: -1
          val lastModified =
            jsonObject.get("lastModified")?.asString ?: "1000-01-01T01:01:01.000Z"

          val modelData = HuggingFaceEntityBasicApiData(
            endpointKind,
            nlsSafeId,
            gated,
            downloads,
            likes,
            lastModified,
            pipelineTag
          )
          modelDataMap[nlsSafeId] = modelData
        }
      }
    }
    return modelDataMap
  }
}
