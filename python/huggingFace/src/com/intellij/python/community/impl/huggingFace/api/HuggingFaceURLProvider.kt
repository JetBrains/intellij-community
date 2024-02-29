// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import java.net.URL

object HuggingFaceURLProvider {

  private val baseURL = PyHuggingFaceBundle.message("python.hugging.face.base.url")
  private val models_expand_parameters = listOf("gated", "downloads", "likes", "lastModified", "pipeline_tag")
  private val datasets_expand_parameters = listOf("gated", "downloads", "likes", "lastModified")

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

  fun getModelApiLink(modelId: String): URL = URL("$baseURL/api/models/$modelId")

  fun getModelSearchQuery(query: String, tags: String? = null, sort: String = "downloads", limit: Int = 20): URL {
    return URL("$baseURL/api/models?search=$query&sort=$sort&limit=$limit&direction=-1"
    ).let { url ->
      tags?.let { URL("${url}&filter=${tags}") } ?: url
    }
  }

  // Will be necessary to fetch detailed information for dataset
  // fun getDatasetApiLink(datasetId: String): URL = URL("$baseURL/api/models/$datasetId")
  // may be needed for dataset viewer button:
  // fun getDatasetViewerLink(datasetName: String): URL = URL("$baseURL/datasets/$datasetName/viewer")

  fun fetchApiDataUrl(
    entityKind: HuggingFaceEntityKind,
    limit: Int = HuggingFaceConstants.HF_API_FETCH_PAGE_SIZE,
    sort: String = HuggingFaceConstants.API_FETCH_SORT_KEY
  ): URL {
    val expand = if (entityKind == HuggingFaceEntityKind.MODEL) {
      models_expand_parameters
    } else {
      datasets_expand_parameters
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

  //fun makeAbsoluteImageLink(modelName: String, relativeImagePath: String): URL =
  //  URL("$baseURL/$modelName/resolve/main/$relativeImagePath")
}
