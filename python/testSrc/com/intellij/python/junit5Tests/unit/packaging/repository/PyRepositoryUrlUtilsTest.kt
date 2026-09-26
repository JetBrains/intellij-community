// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.repository

import com.jetbrains.python.packaging.repository.RepositoryUrl
import com.jetbrains.python.packaging.repository.isValidRepositoryUrl
import com.jetbrains.python.packaging.repository.normalizeRepoUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class PyRepositoryUrlUtilsTest {

  private fun normalize(url: String): String = normalizeRepoUrl(url).value


  @Test
  fun `normalizeRepoUrl lowercases the scheme`() {
    assertEquals("http://example.com/simple", normalize("HTTP://example.com/simple"))
  }

  @Test
  fun `normalizeRepoUrl lowercases the host`() {
    assertEquals("https://example.com/simple", normalize("https://Example.COM/simple"))
  }

  @Test
  fun `normalizeRepoUrl strips a trailing slash`() {
    assertEquals("https://example.com/simple", normalize("https://example.com/simple/"))
  }

  @Test
  fun `normalizeRepoUrl preserves an explicit port`() {
    assertEquals("https://example.com:8443/simple", normalize("https://example.com:8443/simple"))
  }

  @Test
  fun `normalizeRepoUrl omits the port when none is given`() {
    val result = normalize("https://example.com/simple")
    assertEquals("https://example.com/simple", result)
    assertFalse(result.removePrefix("https://").substringBefore('/').contains(':'),
                "Result should not contain an inferred port")
  }

  @Test
  fun `normalizeRepoUrl trims surrounding whitespace`() {
    assertEquals("https://example.com/simple", normalize("  https://example.com/simple  "))
  }

  @Test
  fun `normalizeRepoUrl keeps an empty path empty`() {
    assertEquals("https://example.com", normalize("https://example.com"))
  }

  @Test
  fun `normalizeRepoUrl produces the same value for equivalent inputs`() {
    assertEquals(
      normalize("HTTP://Example.COM/simple/"),
      normalize("http://example.com/simple"),
    )
  }

  @Test
  fun `normalizeRepoUrl falls back to trimmed strip-trailing-slash on a malformed URI`() {
    // Space inside the authority triggers URISyntaxException, exercising the catch branch.
    assertEquals("http:// example.com", normalize("  http:// example.com/  "))
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "   ", "\t"])
  fun `isValidRepositoryUrl rejects blank and empty`(url: String) {
    assertFalse(isValidRepositoryUrl(url))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "http://example.com",
    "https://example.com/simple",
    "HTTPS://Example.com",
    "http://example.com:8443/simple/",
  ])
  fun `isValidRepositoryUrl accepts http and https`(url: String) {
    assertTrue(isValidRepositoryUrl(url))
  }

  @ParameterizedTest
  @CsvSource(value = [
    "ftp://example.com",
    "file:///tmp/x",
    "ssh://git@example.com/repo.git",
    "example.com",
    "http:// example.com", // malformed URI, exercises URISyntaxException branch
  ])
  fun `isValidRepositoryUrl rejects non-http schemes and malformed URIs`(url: String) {
    assertFalse(isValidRepositoryUrl(url))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "http://example.com",
    "https://example.com/simple",
    "  https://example.com/simple  ",
  ])
  fun `RepositoryUrl isValid accepts well-formed http and https`(raw: String) {
    assertTrue(RepositoryUrl(raw).isValid())
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "   ",
    "http://",
    "https://",
    "ftp://example.com",
    "not a url",
  ])
  fun `RepositoryUrl isValid rejects blank scheme-only or malformed`(raw: String) {
    assertFalse(RepositoryUrl(raw).isValid())
  }
}
