// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import org.jetbrains.annotations.ApiStatus
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApiStatus.Internal
object HuggingFaceURLProvider {
  // todo: there must be already existing solutions for URL building in the platform, find and apply them
  private val baseURL = PyHuggingFaceBundle.message("python.hugging.face.base.url")
  private val modelsExpandParameters = listOf("gated", "downloads", "likes", "lastModified", "pipeline_tag", "library_name")
  private val datasetsExpandParameters = listOf("gated", "downloads", "likes", "lastModified")

  fun getEntityMarkdownURL(entityId: String, entityKind: HuggingFaceEntityKind): URL {
    return when(entityKind) {
      HuggingFaceEntityKind.MODEL -> URL("$baseURL/$entityId/raw/main/README.md")
      HuggingFaceEntityKind.DATASET -> URL("$baseURL/datasets/$entityId/raw/main/README.md")
    }
  }

  fun getEntityCardLink(entityId: String, entityKind: HuggingFaceEntityKind): URL {
    return when(entityKind) {
      HuggingFaceEntityKind.MODEL -> getModelCardLink(entityId)
      HuggingFaceEntityKind.DATASET -> getDatasetCardLink(entityId)
    }
  }

  fun getModelCardLink(modelId: String): URL = URL("$baseURL/$modelId")

  fun getDatasetCardLink(datasetId: String): URL = URL("$baseURL/datasets/$datasetId")

  private fun createCommonParametersString(commonParameters: Map<String, String>): String {
    return commonParameters.entries.joinToString("&") {
      val key = URLEncoder.encode(it.key, StandardCharsets.UTF_8)
      val value = URLEncoder.encode(it.value, StandardCharsets.UTF_8)
      "$key=$value"
    }
  }

  private fun createExpandParametersString(expandParameters: List<String>): String {
    return expandParameters.joinToString("&") {
      val key = URLEncoder.encode("expand[]", StandardCharsets.UTF_8)
      val value = URLEncoder.encode(it, StandardCharsets.UTF_8)
      "$key=$value"
    }
  }
  fun getModelSearchQuery(
    query: String,
    tags: String? = null,
    sortKey: HuggingFaceModelSortKey = HuggingFaceModelSortKey.LIKES,
    limit: Int = 25
  ): URL {
    val commonParameters = mapOf(
      "search" to query,
      "sort" to sortKey.value,
      "limit" to limit.toString(),
      "direction" to "-1"
    )
    val expandParameters = modelsExpandParameters

    val encodedCommonParameters = createCommonParametersString(commonParameters)
    val encodedExpandParameters = createExpandParametersString(expandParameters)

    val filterParameter = if (tags.isNullOrBlank()) "" else "&filter=${URLEncoder.encode(tags, StandardCharsets.UTF_8)}"
    val allParameters = "$encodedCommonParameters&$encodedExpandParameters$filterParameter"

    return URL("$baseURL/api/models?$allParameters")
  }

  fun fetchApiDataUrl(
    entityKind: HuggingFaceEntityKind,
    limit: Int = HuggingFaceConstants.HF_API_FETCH_PAGE_SIZE,
    sort: String = HuggingFaceConstants.API_FETCH_SORT_KEY
  ): URL {
    val commonParameters = mapOf(
      "limit" to limit.toString(),
      "sort" to sort,
      "direction" to "-1"
    )

    val expandParameters = when(entityKind) {
      HuggingFaceEntityKind.MODEL -> modelsExpandParameters
      HuggingFaceEntityKind.DATASET -> datasetsExpandParameters
    }

    val encodedCommonParameters = createCommonParametersString(commonParameters)
    val encodedExpandParameters = createExpandParametersString(expandParameters)

    val allParameters = "$encodedCommonParameters&$encodedExpandParameters"

    return URL("$baseURL/api/${entityKind.urlFragment}?$allParameters")
  }

  fun makeAbsoluteFileLink(entityId: String, relativeFilePath: String): URL =
    URL("$baseURL/$entityId/blob/main/$relativeFilePath")
}
