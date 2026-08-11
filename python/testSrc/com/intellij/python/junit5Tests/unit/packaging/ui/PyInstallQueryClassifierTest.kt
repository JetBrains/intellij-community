// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.ui

import com.jetbrains.python.packaging.toolwindow.ui.isLocalPath
import com.jetbrains.python.packaging.toolwindow.ui.isPackageUrl
import com.jetbrains.python.packaging.toolwindow.ui.parseCliCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class PyInstallQueryClassifierTest {

  @ParameterizedTest
  @ValueSource(strings = [
    "https://example.com/pkg.whl",
    "http://example.com/foo.tar.gz",
    "git+https://github.com/me/proj.git",
    "file:///tmp/foo.whl",
  ])
  fun `isPackageUrl matches URLs with a scheme`(url: String) {
    assertTrue(isPackageUrl(url))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "requests",
    "requests==2.31.0",
    "./local-pkg",
    "/abs/path",
  ])
  fun `isPackageUrl rejects plain package names and paths`(text: String) {
    assertFalse(isPackageUrl(text))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "./local-pkg",
    ".",
    "..",
    "./",
  ])
  fun `isLocalPath matches dot-relative paths`(text: String) {
    assertTrue(isLocalPath(text))
  }

  @Test
  fun `isLocalPath rejects bare names that happen to look like packages`() {
    assertFalse(isLocalPath("requests"))
    assertFalse(isLocalPath("urllib3"))
  }

  @Test
  fun `parseCliCommand returns null for blank input`() {
    assertNull(parseCliCommand(""))
    assertNull(parseCliCommand("   "))
    assertNull(parseCliCommand("\t\n"))
  }

  @Test
  fun `parseCliCommand splits tool from args on whitespace`() {
    val parsed = parseCliCommand("pip install requests")
    assertEquals("pip", parsed?.toolName)
    assertEquals(listOf("install", "requests"), parsed?.args)
  }

  @Test
  fun `parseCliCommand collapses runs of whitespace`() {
    val parsed = parseCliCommand("  uv\tpip   install   numpy  ")
    assertEquals("uv", parsed?.toolName)
    assertEquals(listOf("pip", "install", "numpy"), parsed?.args)
  }

  @Test
  fun `parseCliCommand returns empty args list when only the tool name is present`() {
    val parsed = parseCliCommand("uv")
    assertEquals("uv", parsed?.toolName)
    assertEquals(emptyList<String>(), parsed?.args)
  }
}
