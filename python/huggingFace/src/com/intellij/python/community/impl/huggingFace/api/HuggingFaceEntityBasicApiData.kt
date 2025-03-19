// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil.humanReadableNumber
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class HuggingFaceEntityBasicApiData (
  @JsonProperty("kind")
  val kind: HuggingFaceEntityKind = HuggingFaceEntityKind.MODEL,
  @JsonProperty("id")
  @Nls val itemId: String = "",
  @JsonProperty("gated")
  val gated: String = "true",
  @JsonProperty("downloads")
  val downloads: Int = 0,
  @JsonProperty("likes")
  val likes: Int = 0,
  @JsonProperty("lastModified")
  val lastModified: String = "",
  @JsonProperty("library_name")
  val libraryName: String? = null,
  @JsonProperty("pipeline_tag")
  @NlsSafe val pipelineTag: String = HuggingFaceConstants.UNDEFINED_PIPELINE_TAG
) {
  val humanReadableLikes: String = humanReadableNumber(likes)
  val humanReadableDownloads: String = humanReadableNumber(downloads)
  val humanReadableLastUpdated: String =
    LocalDateTime.parse(lastModified, DateTimeFormatter.ISO_DATE_TIME)
      .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
