// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCache
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceSafeExecutor

object HuggingFaceApi {
  fun fillCacheWithBasicApiData(endpoint: HuggingFaceEntityKind, cache: HuggingFaceCache, maxCount: Int) {
    val executor = HuggingFaceSafeExecutor.instance

    executor.asyncSuspend("FetchHuggingFace$endpoint") {
      var nextPageUrl: String? = HuggingFaceURLProvider.fetchApiDataUrl(endpoint).toString()

      while (nextPageUrl != null && cache.getCacheSize() < maxCount) {
        val response = HuggingFaceHttpClient.downloadContentAndHeaders(nextPageUrl)
        response.content?.let {
          val dataMap = parseBasicEntityData(endpoint, it)
          cache.saveEntities(dataMap)
        }
        nextPageUrl = extractNextPageUrl(response.linkHeader)
      }
    }
  }

  //suspend fun parseDetailedModelData(modelId: String): String? {
  //  val modelApiLink = HuggingFaceURLProvider.getModelApiLink(modelId).toString()
  //  val response = HuggingFaceHttpClient.downloadContentAndHeaders(modelApiLink)
  //  return response.content
  //}

  suspend fun performSearch(query: String, tags: String?): Map<String, HuggingFaceEntityBasicApiData> {
    val queryUrl = HuggingFaceURLProvider.getModelSearchQuery(query, tags)
    val executor = HuggingFaceSafeExecutor.instance

    return executor.asyncSuspend("SearchHuggingFace") {
      // todo: add pagination - but only if needed (user scrolls down)
      var nextPageUrl: String? = queryUrl.toString()
      val response = HuggingFaceHttpClient.downloadContentAndHeaders(nextPageUrl!!)
      response.content?.let {
        val dataMap = parseBasicEntityData(HuggingFaceEntityKind.MODEL, it)
        dataMap
      } ?: emptyMap()
    }.await()
  }

  private fun extractNextPageUrl(linkHeader: String?): String? {
    val nextLinkMatch = Regex("""<(.+)>; rel="next"""").find(linkHeader ?: "")
    return nextLinkMatch?.groupValues?.get(1)
  }

  private fun parseBasicEntityData(
    endpointKind: HuggingFaceEntityKind,
    json: String
  ): Map<String, HuggingFaceEntityBasicApiData> {
    val objectMapper = ObjectMapper().registerKotlinModule()
    val jsonArray: JsonNode = objectMapper.readTree(json)
    val modelDataMap = mutableMapOf<String, HuggingFaceEntityBasicApiData>()

    jsonArray.forEach { element ->
      val jsonObject: JsonNode = element
      jsonObject.get("id")?.asText()?.let { id ->
        if (id.isNotEmpty()) {
          @NlsSafe val nlsSafeId = id
          @NlsSafe val pipelineTag = jsonObject.get("pipeline_tag")?.asText() ?: "unknown"
          val gated = jsonObject.get("gated")?.asText() ?: "true"
          val downloads = jsonObject.get("downloads")?.asInt() ?: -1
          val likes = jsonObject.get("likes")?.asInt() ?: -1
          val lastModified = jsonObject.get("lastModified")?.asText() ?: "1000-01-01T01:01:01.000Z"
          val libraryName = jsonObject.get("library_name")?.asText() ?: "unknown"

          val modelData = HuggingFaceEntityBasicApiData(
            endpointKind,
            nlsSafeId,
            gated,
            downloads,
            likes,
            lastModified,
            libraryName,
            pipelineTag,
          )
          modelDataMap[nlsSafeId] = modelData
        }
      }
    }
    return modelDataMap
  }
}
