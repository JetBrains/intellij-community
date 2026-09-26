// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.python.uv.backend.toSystemPythons
import com.intellij.python.uv.backend.cli.uv.UvPythonEntry
import com.intellij.python.uv.backend.cli.uv.UvPythonVersionParts
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.div

/**
 * Turning `uv python list` into the interpreters the widget offers as a base Python.
 *
 * What is asserted here is what the widget depends on and uv does not promise: that one install listed under several
 * names is offered once, that uv's own builds are told from the ones it merely found, and that the order is the one the
 * list is read in. The entries are built by hand rather than parsed, so this tests the mapping and not the parser —
 * `UvPythonListParserTest` covers that.
 */
class UvSystemPythonMappingTest {
  private fun entry(
    version: String,
    parts: Triple<Int, Int, Int>,
    path: Path?,
    variant: String = "default",
    url: String? = null,
  ): UvPythonEntry = UvPythonEntry(
    key = "cpython-$version-macos-aarch64-none",
    version = version,
    versionParts = UvPythonVersionParts(parts.first, parts.second, parts.third),
    path = path?.toString(),
    symlink = null,
    url = url,
    implementation = "cpython",
    variant = variant,
  )

  /** An interpreter file at [at], parents created — enough for a real path to resolve. */
  private fun interpreterAt(at: Path): Path {
    at.parent.createDirectories()
    at.createFile()
    return at
  }

  @Test
  fun `an install listed under several names is offered once`(@TempDir tmp: Path) {
    val real = interpreterAt(tmp / "brew" / "bin" / "python3.14")
    val alias = (tmp / "brew" / "bin" / "python3").createSymbolicLinkPointingTo(real)

    val pythons = listOf(
      entry("3.14.5", Triple(3, 14, 5), real),
      entry("3.14.5", Triple(3, 14, 5), alias),
    ).toSystemPythons(uvPythonDir = null)

    assertEquals(1, pythons.size, "the same interpreter under two names is one Python")
    // The first name uv gave wins, which is the one its own ordering put first.
    assertEquals(real, pythons.single().pythonBinary)
  }

  @Test
  fun `an entry uv could only download is not an interpreter here`(@TempDir tmp: Path) {
    val pythons = listOf(
      entry("3.13.5", Triple(3, 13, 5), interpreterAt(tmp / "bin" / "python3.13")),
      entry("3.15.0b4", Triple(3, 15, 0), path = null, url = "https://example.invalid/cpython-3.15.0b4.tar.gz"),
    ).toSystemPythons(uvPythonDir = null)

    assertEquals(listOf(LanguageLevel.PYTHON313), pythons.map { it.languageLevel })
  }

  @Test
  fun `only what lives under uv's own directory is uv-managed`(@TempDir tmp: Path) {
    val uvDir = tmp / "uv" / "python"
    val managed = interpreterAt(uvDir / "cpython-3.13-macos-aarch64-none" / "bin" / "python3.13")
    val found = interpreterAt(tmp / "brew" / "bin" / "python3.12")

    val pythons = listOf(
      // The key carries the full version while the directory carries the short one — which is why the directory, and
      // not the key, is what decides this.
      entry("3.13.11", Triple(3, 13, 11), managed),
      entry("3.12.1", Triple(3, 12, 1), found),
    ).toSystemPythons(uvPythonDir = uvDir)

    assertTrue(pythons.first { it.pythonBinary == managed }.uvManaged)
    assertFalse(pythons.first { it.pythonBinary == found }.uvManaged)
  }

  @Test
  fun `newest first, and a free-threaded build after the ordinary one`(@TempDir tmp: Path) {
    val pythons = listOf(
      entry("3.12.1", Triple(3, 12, 1), interpreterAt(tmp / "a" / "python3.12")),
      entry("3.14.5", Triple(3, 14, 5), interpreterAt(tmp / "b" / "python3.14"), variant = "freethreaded"),
      entry("3.13.5", Triple(3, 13, 5), interpreterAt(tmp / "c" / "python3.13")),
    ).toSystemPythons(uvPythonDir = null)

    assertEquals(
      listOf(LanguageLevel.PYTHON313, LanguageLevel.PYTHON312, LanguageLevel.PYTHON314),
      pythons.map { it.languageLevel },
      "the ordinary builds lead, newest first, and the free-threaded 3.14 follows them",
    )
    assertTrue(pythons.last().freeThreaded)
  }

  @Test
  fun `the version is uv's own string, not the language level`(@TempDir tmp: Path) {
    val pythons = listOf(entry("3.13.11", Triple(3, 13, 11), interpreterAt(tmp / "bin" / "python3.13")))
      .toSystemPythons(uvPythonDir = null)

    assertEquals("3.13.11", pythons.single().version)
    assertEquals(LanguageLevel.PYTHON313, pythons.single().languageLevel)
  }
}
