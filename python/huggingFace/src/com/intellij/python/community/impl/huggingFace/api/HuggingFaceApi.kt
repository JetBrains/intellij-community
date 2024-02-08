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
import org.codehaus.jettison.json.JSONArray
import org.codehaus.jettison.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration


data class HuggingFaceExtendedApiModelData(
  val id: String,
  val author: String,
  val lastModified: String,
  val gated: Boolean,
  val pipelineTag: String?,
  val libraryName: String?,
  val likes: Int,
  val config: String,
  val cardData: CardDataBase,
  val transformersInfo: TransformersInfoBase?
)

data class CardDataBase(
  val language: List<String>?,
  val tags: List<String>?,
  val license: String?
)

data class TransformersInfoBase(
  val autoModel: String,
  val pipelineTag: String,
  val processor: String
)


object HuggingFaceApi {
  @Suppress("unused")  // will be necessary for future tasks like PY-63671
  suspend fun fetchDetailedModelDataFromApi(modelID: String): HuggingFaceExtendedApiModelData? {
    val apiEndpoint = HuggingFaceURLProvider.getModelApiLink(modelID)

    val huggingFaceExecutor = HuggingFaceSafeExecutor.instance
    return huggingFaceExecutor.asyncSuspend("Fetching Model Data") {
      val connection: HttpURLConnection = apiEndpoint.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"

      if (connection.responseCode == 200) {
        val rawJsonResponse: String = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonResponse = JSONObject(rawJsonResponse)

        HuggingFaceExtendedApiModelData(
          id = jsonResponse.getString("id"),
          author = jsonResponse.getString("author"),
          lastModified = jsonResponse.getString("lastModified"),
          gated = jsonResponse.getString("gated").toBoolean(),
          pipelineTag = jsonResponse.optString("pipeline_tag", null),
          libraryName = jsonResponse.optString("library_name", null),
          likes = jsonResponse.getInt("likes"),
          config = jsonResponse.getString("config"),
          cardData = jsonResponse.getJSONObject("cardData").let {
            CardDataBase(
              language = it.optJSONArray("language")?.let { array -> List(array.length()) { i -> array.getString(i) } },
              tags = it.optJSONArray("tags")?.let { array -> List(array.length()) { i -> array.getString(i) } },
              license = it.optString("license", null)
            )
          },
          transformersInfo = jsonResponse.optJSONObject("transformersInfo")?.let {
            TransformersInfoBase(
              autoModel = it.getString("auto_model"),
              pipelineTag = it.getString("pipeline_tag"),
              processor = it.getString("processor")
            )
          }
        )
      } else {
        null
      }
    }.await()
  }

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

  private fun parseBasicEntityData(endpointKind: HuggingFaceEntityKind, json: String): Map<String, HuggingFaceEntityBasicApiData> {
    val jsonArray = JSONArray(json)
    val modelDataMap = mutableMapOf<String, HuggingFaceEntityBasicApiData>()

    for (i in 0 until jsonArray.length()) {
      val jsonObject = jsonArray.getJSONObject(i)
      jsonObject.optString("id").let {id: String ->
        if (id.isNotEmpty()) {

          @NlsSafe val nlsSafeId = id
          @NlsSafe val pipelineTag = jsonObject.optString("pipeline_tag", "unknown")

          val modelData = HuggingFaceEntityBasicApiData(
            endpointKind,
            nlsSafeId,
            jsonObject.optString("gated", "true"),
            jsonObject.optInt("downloads", -1),
            jsonObject.optInt("likes", -1),
            jsonObject.optString("lastModified", "1000-01-01T01:01:01.000Z"),
            pipelineTag
          )
          modelDataMap[id] = modelData
        }
      }
    }
    return modelDataMap
  }
}
