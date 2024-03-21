// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import org.jetbrains.annotations.ApiStatus
import java.net.URL

@ApiStatus.Internal
object HuggingFaceURLProvider {

  private val baseURL = PyHuggingFaceBundle.message("python.hugging.face.base.url")
  private val modelsExpandParameters = listOf("gated", "downloads", "likes", "lastModified", "pipeline_tag")
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

  fun getModelSearchQuery(query: String,
                          tags: String? = null,
                          sortKey: HuggingFaceModelSortKey = HuggingFaceModelSortKey.DOWNLOADS,
                          limit: Int = 20
  ): URL {
    val urlString = "$baseURL/api/models?search=$query&sort=${sortKey.value}&limit=$limit&direction=-1&expand[]=gated&expand[]=downloads&expand[]=likes&expand[]=lastModified&expand[]=pipeline_tag&expand=library_name"
    return URL(urlString + if (tags.isNullOrBlank()) "" else "&filter=$tags")
  }

  fun fetchApiDataUrl(
    entityKind: HuggingFaceEntityKind,
    limit: Int = HuggingFaceConstants.HF_API_FETCH_PAGE_SIZE,
    sort: String = HuggingFaceConstants.API_FETCH_SORT_KEY
  ): URL {
    val expand = if (entityKind == HuggingFaceEntityKind.MODEL) {
      modelsExpandParameters
    } else {
      datasetsExpandParameters
    }

    val parameters = mapOf(
      "limit" to limit.toString(),
      "sort" to sort,
      "direction" to "-1",
      "expand[]" to expand
    )

    val query = parameters.map { (key, value) ->
      when(value) {
        is List<*> -> value.joinToString("&") { "$key=$it" }
        else -> "$key=$value"
      }
    }.joinToString("&")

    val url = URL("$baseURL/api/${entityKind.urlFragment}?$query")
    return url
  }

  fun makeAbsoluteFileLink(entityId: String, relativeFilePath: String): URL =
    URL("$baseURL/$entityId/blob/main/$relativeFilePath")
}
