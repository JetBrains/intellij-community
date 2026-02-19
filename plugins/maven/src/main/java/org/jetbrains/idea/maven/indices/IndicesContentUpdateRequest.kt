// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import org.jetbrains.idea.maven.model.MavenRepositoryInfo

data class IndicesContentUpdateRequest(
  val indicesToUpdate: List<MavenRepositoryInfo>,
  val explicit: Boolean,
  val full: Boolean,
  val showProgress: Boolean
) {
  companion object {
    @JvmStatic
    fun explicit(urls: List<MavenRepositoryInfo>): IndicesContentUpdateRequest {
      return IndicesContentUpdateRequest(urls, true, true, true)
    }

    @JvmStatic
    fun background(urls: List<MavenRepositoryInfo>): IndicesContentUpdateRequest {
      return IndicesContentUpdateRequest(urls, false, true, false)
    }
  }
}
