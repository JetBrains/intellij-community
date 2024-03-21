// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceMdCacheEntry
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceMdCardsCache
import com.intellij.python.community.impl.huggingFace.documentation.HuggingFaceDocumentationPlaceholdersUtil
import com.intellij.python.community.impl.huggingFace.documentation.HuggingFaceReadmeCleaner
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceSafeExecutor
import org.jetbrains.annotations.ApiStatus
import java.time.Instant

@ApiStatus.Internal
object HuggingFaceApi {
  private val nextLinkRegex = Regex("""<(.+)>; rel="next"""")

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

  suspend fun performSearch(query: String, tags: String?, sortKey: HuggingFaceModelSortKey): Map<String, HuggingFaceEntityBasicApiData> {
    val queryUrl = HuggingFaceURLProvider.getModelSearchQuery(query, tags, sortKey)
    val executor = HuggingFaceSafeExecutor.instance

    return executor.asyncSuspend("SearchHuggingFace") {
      // todo: add pagination - but only if needed (user scrolls down)
      val nextPageUrl: String = queryUrl.toString()
      val response = HuggingFaceHttpClient.downloadContentAndHeaders(nextPageUrl)
      response.content?.let {
        val dataMap = parseBasicEntityData(HuggingFaceEntityKind.MODEL, it)
        dataMap
      } ?: emptyMap()
    }.await()
  }

  private fun extractNextPageUrl(linkHeader: String?): String? {
    val nextLinkMatch = nextLinkRegex.find(linkHeader ?: "")
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
      val id = jsonObject.get("id")?.asText()?.takeIf { it.isNotEmpty() } ?: return@forEach

      @NlsSafe val nlsSafeId = id
      @NlsSafe val pipelineTag = jsonObject.get("pipeline_tag")?.asText() ?: HuggingFaceConstants.UNDEFINED_PIPELINE_TAG
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

    return modelDataMap
  }

  fun fetchOrRetrieveModelCard(entityDataApiContent: HuggingFaceEntityBasicApiData,
                               entityId: String,
                               entityKind: HuggingFaceEntityKind,
                               prefix: String = "markdown"): String {
    return if (entityDataApiContent.gated != "false") {
      HuggingFaceDocumentationPlaceholdersUtil.generateGatedEntityMarkdownString(entityId, entityKind)
    }
    else {
      val cached = HuggingFaceMdCardsCache.getData("${prefix}_${entityId}")
      cached?.data
      ?: try {
        val mdUrl = HuggingFaceURLProvider.getEntityMarkdownURL(entityId, entityKind).toString()
        val rawData = HuggingFaceHttpClient.downloadFile(mdUrl)
                      ?: HuggingFaceDocumentationPlaceholdersUtil.noInternetConnectionPlaceholder(entityId)
        val cleanedData = HuggingFaceReadmeCleaner(rawData, entityId, entityKind).doCleanUp().getMarkdown()
        HuggingFaceMdCardsCache.saveData("markdown_$entityId", HuggingFaceMdCacheEntry(cleanedData, Instant.now()))
        cleanedData
      }
      catch (e: Exception) {
        HuggingFaceDocumentationPlaceholdersUtil.noInternetConnectionPlaceholder(entityId)
      }
    }
  }
}
