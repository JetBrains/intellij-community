// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import org.jetbrains.annotations.Nls
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


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

  fun humanReadableLastUpdated(): String {
    val parsedDate = LocalDateTime.parse(lastModified, DateTimeFormatter.ISO_DATE_TIME)
    return parsedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
  }
}
