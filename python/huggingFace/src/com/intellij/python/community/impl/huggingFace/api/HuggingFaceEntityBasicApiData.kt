// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import org.jetbrains.annotations.Nls
import org.codehaus.jettison.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


data class HuggingFaceEntityBasicApiData (
  val kind: HuggingFaceEntityKind,
  @Nls val itemId: String,
  val gated: String,
  val downloads: Int,
  val likes: Int,
  val lastModified: String,
  @NlsSafe val pipelineTag: String
) {
  @Suppress("unused")
  fun toJson(): JSONObject {
    return JSONObject(mapOf(
      "kind" to kind.ordinal,
      "itemId" to itemId,
      "gated" to gated,
      "downloads" to downloads,
      "likes" to likes,
      "lastModified" to lastModified,
      "pipeline_tag" to pipelineTag
    ))
  }

  companion object {
    @Suppress("unused")
    fun fromJson(json: JSONObject): HuggingFaceEntityBasicApiData {
      @NlsSafe val itemId = json.getString("itemId")
      @NlsSafe val pipelineTag = json.getString("pipeline_tag")
      return HuggingFaceEntityBasicApiData(
        HuggingFaceEntityKind.entries[json.getInt("kind")],
        itemId,
        json.getString("gated"),
        json.getInt("downloads"),
        json.getInt("likes"),
        json.getString("lastModified"),
        pipelineTag
      )
    }
  }

  @Nls
  private fun humanReadableNumber(rawNumber: Int): String {
    if (rawNumber < 1000) return rawNumber.toString()
    val suffixes = arrayOf("K", "M", "B", "T")
    var count = rawNumber.toDouble()
    var i = 0
    while (i < suffixes.size && count >= 1000) {
      count /= 1000
      i++
    }
    return String.format("%.1f%s", count, suffixes[i - 1])
  }

  @Nls
  fun humanReadableLikes(): String {
    return humanReadableNumber(likes)
  }

  @Nls
  fun humanReadableDownloads(): String {
    return humanReadableNumber(downloads)
  }

  @NlsSafe
  fun humanReadableLastUpdated(): String {
    val parsedDate = LocalDateTime.parse(lastModified, DateTimeFormatter.ISO_DATE_TIME)
    return parsedDate.format(DateTimeFormatter.ofPattern("MMM dd"))
  }
}