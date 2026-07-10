// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Subsystems.PackagingRequirements
@Layers.Functional
class PythonPackageMetadataTest {

  @Test
  fun `safeProjectUrls keeps only http and https entries`() {
    val metadata = PythonPackageMetadata(projectUrls = linkedMapOf(
      "Homepage" to "https://example.com",
      "Docs" to "http://example.com/docs",
    ))
    assertEquals(mapOf("Homepage" to "https://example.com", "Docs" to "http://example.com/docs"), metadata.safeProjectUrls)
  }

  @Test
  fun `safeProjectUrls drops javascript and other unsafe schemes`() {
    val metadata = PythonPackageMetadata(projectUrls = linkedMapOf(
      "Homepage" to "javascript:alert(1)",
      "Docs" to "data:text/html,<script>alert(1)</script>",
      "Source" to "file:///etc/passwd",
      "Mail" to "mailto:a@b.c",
      "Relative" to "//evil.example.com",
    ))
    assertTrue(metadata.safeProjectUrls.isEmpty(), "unsafe schemes must be dropped: ${metadata.safeProjectUrls}")
  }

  @Test
  fun `safeProjectUrls scheme check is case-insensitive`() {
    val metadata = PythonPackageMetadata(projectUrls = mapOf("Homepage" to "HTTPS://Example.com"))
    assertEquals(mapOf("Homepage" to "HTTPS://Example.com"), metadata.safeProjectUrls)
  }

  @Test
  fun `preferredProjectUrl skips an unsafe entry and falls back to the next safe priority`() {
    // Homepage has the highest priority but an unsafe scheme, so Source (next present priority) wins.
    val metadata = PythonPackageMetadata(projectUrls = linkedMapOf(
      "Homepage" to "javascript:alert(1)",
      "Source" to "https://example.com/src",
    ))
    val preferred = metadata.preferredProjectUrl()
    assertEquals("Source", preferred?.label)
    assertEquals("https://example.com/src", preferred?.url)
  }

  @Test
  fun `preferredProjectUrl returns null when the only candidate is unsafe`() {
    val metadata = PythonPackageMetadata(projectUrls = mapOf("Homepage" to "javascript:alert(1)"))
    assertNull(metadata.preferredProjectUrl())
  }

  @Test
  fun `preferredProjectUrl returns the safe homepage when present`() {
    val metadata = PythonPackageMetadata(projectUrls = linkedMapOf(
      "Homepage" to "https://example.com",
      "Source" to "https://example.com/src",
    ))
    val preferred = metadata.preferredProjectUrl()
    assertEquals("Homepage", preferred?.label)
    assertEquals("https://example.com", preferred?.url)
  }
}
