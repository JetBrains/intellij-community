// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil.humanReadableNumber
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


@ApiStatus.Internal
data class HuggingFaceEntityBasicApiData (
  val kind: HuggingFaceEntityKind,
  @Nls val itemId: String,
  val gated: String,
  val downloads: Int,
  val likes: Int,
  val lastModified: String,
  val libraryName: String?,
  @NlsSafe val pipelineTag: String
) {
  val humanReadableLikes: String = humanReadableNumber(likes)
  val humanReadableDownloads: String = humanReadableNumber(downloads)
  val humanReadableLastUpdated: String =
    LocalDateTime.parse(lastModified, DateTimeFormatter.ISO_DATE_TIME)
      .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
