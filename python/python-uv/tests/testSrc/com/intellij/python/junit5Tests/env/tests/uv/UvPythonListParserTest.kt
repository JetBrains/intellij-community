// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.python.uv.backend.cli.uv.parseUvPythonList
import com.jetbrains.python.getOrNull
import com.jetbrains.python.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * Parsing of `uv python list --output-format json`, against output captured from a real uv.
 *
 * The parser is what tells an interpreter that is on the machine from one uv would have to fetch, and what supplies the
 * `major.minor` an environment is created at — both invisible in the table format this replaced, which a regex reduced to
 * bare version numbers.
 */
class UvPythonListParserTest {
  /**
   * One entry of each kind uv emits: installed (a path, no URL), downloadable (a URL, no path), a free-threaded variant,
   * a non-CPython implementation, and a pre-release whose version string is not a dotted triple.
   */
  private val sample: String = """
    [
      {
        "key": "cpython-3.14.5-macos-aarch64-none",
        "version": "3.14.5",
        "version_parts": { "major": 3, "minor": 14, "patch": 5 },
        "path": "/opt/homebrew/bin/python3.14",
        "symlink": "../Cellar/python@3.14/3.14.5/bin/python3.14",
        "url": null,
        "os": "macos",
        "variant": "default",
        "implementation": "cpython",
        "arch": "aarch64",
        "libc": "none"
      },
      {
        "key": "cpython-3.15.0b4+freethreaded-macos-aarch64-none",
        "version": "3.15.0b4",
        "version_parts": { "major": 3, "minor": 15, "patch": 0 },
        "path": null,
        "symlink": null,
        "url": "https://releases.astral.sh/cpython-3.15.0b4-freethreaded.tar.gz",
        "os": "macos",
        "variant": "freethreaded",
        "implementation": "cpython",
        "arch": "aarch64",
        "libc": "none"
      },
      {
        "key": "pypy-3.11.13-macos-aarch64-none",
        "version": "3.11.13",
        "version_parts": { "major": 3, "minor": 11, "patch": 13 },
        "path": null,
        "symlink": null,
        "url": "https://downloads.python.org/pypy/pypy3.11-v7.3.20-macos_arm64.tar.bz2",
        "os": "macos",
        "variant": "default",
        "implementation": "pypy",
        "arch": "aarch64",
        "libc": "none"
      }
    ]
  """.trimIndent()

  @Test
  fun testInstalledEntry() {
    val installed = parseUvPythonList(sample).getOrThrow().first()
    assertEquals("3.14.5", installed.version)
    assertEquals("/opt/homebrew/bin/python3.14", installed.path)
    assertEquals("../Cellar/python@3.14/3.14.5/bin/python3.14", installed.symlink)
    // An interpreter that is already here is not a download, however uv orders its list.
    assertFalse(installed.isDownloadable, "an entry with a path is installed, not downloadable")
    assertFalse(installed.isFreeThreaded)
    assertEquals("3.14", installed.versionParts.languageLevel)
  }

  @Test
  fun testDownloadableFreeThreadedEntry() {
    val download = parseUvPythonList(sample).getOrThrow()[1]
    assertNull(download.path)
    assertTrue(download.isDownloadable, "an entry with a url and no path has to be fetched first")
    assertTrue(download.isFreeThreaded, "the freethreaded variant is what qualifies this build")
    // The point of reading uv's own split: "3.15.0b4" does not parse as a dotted triple, so the language level the
    // environment would be created at is only available because uv reported it separately.
    assertEquals("3.15", download.versionParts.languageLevel)
  }

  @Test
  fun testNonCPythonImplementationIsKept() {
    val pypy = parseUvPythonList(sample).getOrThrow()[2]
    assertEquals("pypy", pypy.implementation)
    assertTrue(pypy.isDownloadable)
    assertFalse(pypy.isFreeThreaded, "pypy here is a default-variant build; only 'freethreaded' is free-threaded")
  }

  /** uv keeps adding fields to this output; an unknown one must not cost us the whole list. */
  @Test
  fun testUnknownFieldsAreIgnored() {
    val withExtraField = sample.replace("\"os\": \"macos\"", "\"os\": \"macos\", \"invented_by_a_later_uv\": 42")
    assertEquals(3, parseUvPythonList(withExtraField).getOrThrow().size)
  }

  @Test
  fun testEmptyListIsNotAnError() {
    // uv answers with an empty array for a constraint nothing satisfies; that is a result, not a failure to parse.
    assertTrue(parseUvPythonList("[]").getOrThrow().isEmpty(), "an empty array parses to an empty list")
  }

  @Test
  fun testMalformedOutputFails(testInfo: TestInfo) {
    // uv's error path can leave non-JSON on stdout; report that rather than throwing out of the provider.
    assertNull(parseUvPythonList("not json at all").getOrNull(), testInfo.displayName)
    // A well-formed document of the wrong shape has to fail the same way.
    assertNull(parseUvPythonList("""{"pythons": []}""").getOrNull(), testInfo.displayName)
    // A required field missing is the shape being wrong, not a field we tolerate.
    assertNull(parseUvPythonList("""[{"key": "cpython-3.14.5", "version": "3.14.5"}]""").getOrNull(), testInfo.displayName)
  }
}
