// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceMdCacheEntry
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceMdCardsCache
import com.intellij.python.community.impl.huggingFace.documentation.HuggingFaceDocumentationPlaceholdersUtil
import com.intellij.python.community.impl.huggingFace.documentation.HuggingFaceReadmeCleaner
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceCoroutine
import com.intellij.util.io.HttpRequests.HttpStatusException
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.time.Instant

@ApiStatus.Internal
object HuggingFaceApi {
  private val nextLinkRegex = Regex("""<(.+)>; rel="next"""")

  suspend fun fillCacheWithBasicApiData(
    endpoint: HuggingFaceEntityKind,
    cache: HuggingFaceCache,
    maxCount: Int
  ) {
    var nextPageUrl: String? = HuggingFaceURLProvider.fetchApiDataUrl(endpoint).toString()

    while (nextPageUrl != null && cache.getCacheSize() < maxCount) {
      val response = HuggingFaceHttpClient.downloadContentAndHeaders(nextPageUrl)
        .getOrDefault(HfHttpResponseWithHeaders(null, null))

      response.content?.let {
        val dataMap = parseBasicEntityData(endpoint, it)
        cache.saveEntities(dataMap)
      }
      nextPageUrl = extractNextPageUrl(response.linkHeader)
    }
  }

  suspend fun performSearch(query: String, tags: String?, sortKey: HuggingFaceModelSortKey
  ): Map<String, HuggingFaceEntityBasicApiData> = withContext(HuggingFaceCoroutine.Utils.ioScope.coroutineContext) {
    val queryUrl = HuggingFaceURLProvider.getModelSearchQuery(query, tags, sortKey)

    val nextPageUrl: String = queryUrl.toString()
    val response = HuggingFaceHttpClient.downloadContentAndHeaders(nextPageUrl)
      .getOrDefault(HfHttpResponseWithHeaders(null, null))
    response.content?.let {
      val dataMap = parseBasicEntityData(HuggingFaceEntityKind.MODEL, it)
      dataMap
    } ?: emptyMap()
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
    val jsonArray: ArrayNode = objectMapper.readTree(json) as ArrayNode
    val modelDataMap = mutableMapOf<String, HuggingFaceEntityBasicApiData>()

    jsonArray.forEach { jsonNode ->
      val data = objectMapper.treeToValue(jsonNode, HuggingFaceEntityBasicApiData::class.java).copy(kind = endpointKind)
      modelDataMap[data.itemId] = data
    }

    return modelDataMap
  }

  @Nls
  suspend fun fetchOrRetrieveModelCard(entityDataApiContent: HuggingFaceEntityBasicApiData,
                               entityId: String,
                               entityKind: HuggingFaceEntityKind,
                               prefix: String = "markdown"): String {
    if (entityDataApiContent.gated != "false") return HuggingFaceDocumentationPlaceholdersUtil.generateGatedEntityMarkdownString(entityId, entityKind)
    val cached = HuggingFaceMdCardsCache.getData("${prefix}_${entityId}")
    cached?.let { return cached.data }

    val mdUrl = HuggingFaceURLProvider.getEntityMarkdownURL(entityId, entityKind).toString()
    val rawDataResult = HuggingFaceHttpClient.downloadFile(mdUrl)

    return rawDataResult.fold(
      onSuccess = { rawData ->
        val cleanedData = HuggingFaceReadmeCleaner(rawData, entityId, entityKind).doCleanUp().getMarkdown()
        HuggingFaceMdCardsCache.saveData("markdown_$entityId", HuggingFaceMdCacheEntry(cleanedData, Instant.now().epochSecond))
        cleanedData
      },
      onFailure = { exception ->
        when(exception) {
          is HttpStatusException -> when(exception.statusCode) {
            404 -> HuggingFaceDocumentationPlaceholdersUtil.notFoundErrorPlaceholder(entityId)
            else -> HuggingFaceDocumentationPlaceholdersUtil.noInternetConnectionPlaceholder(entityId)
          }
          else -> HuggingFaceDocumentationPlaceholdersUtil.noInternetConnectionPlaceholder(entityId)
        }
      }
    )
  }
}
