// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import org.jetbrains.idea.maven.project.MavenProjectsManagerEx.Companion.getLocalizedTitle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MavenProjectsManagerExUnitTest {

  @Test
  fun testDownloadArtifactLocalizedMessage() {
    assertEquals(
      MavenProjectBundle.message("maven.downloading.short"),
      MavenDownloadSourcesRequest.builder()
        .build()
        .getLocalizedTitle()
    )
    assertEquals(
      MavenProjectBundle.message("maven.downloading.short"),
      MavenDownloadSourcesRequest.builder()
        .withProgressTitle(null)
        .build()
        .getLocalizedTitle()
    )
    assertEquals(
      MavenProjectBundle.message("maven.downloading.sources"),
      MavenDownloadSourcesRequest.builder()
        .withSources()
        .build()
        .getLocalizedTitle()
    )
    assertEquals(
      MavenProjectBundle.message("maven.downloading.documentation"),
      MavenDownloadSourcesRequest.builder()
        .withDocs()
        .build()
        .getLocalizedTitle()
    )
    assertEquals(
      MavenProjectBundle.message("maven.downloading"),
      MavenDownloadSourcesRequest.builder()
        .withDocs()
        .withSources()
        .build()
        .getLocalizedTitle()
    )
    assertEquals(
      "Hello world",
      MavenDownloadSourcesRequest.builder()
        .withDocs()
        .withSources()
        .withProgressTitle("Hello world")
        .build()
        .getLocalizedTitle()
    )
  }
}