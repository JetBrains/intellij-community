// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.python.uv.backend.cli.uv.SELF_UPDATE_TRANSFORMER
import com.intellij.python.uv.backend.cli.uv.UvSelfUpdateResult
import com.intellij.python.uv.backend.cli.uv.parseUvSelfUpdate
import com.jetbrains.python.getOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Parsing of `uv self update`, against output captured from a real uv.
 *
 * Under `--dry-run` this is what decides whether the Package Managers page offers an upgrade for uv, which
 * `uv tool list` cannot answer because uv is not one of the tools uv installs. An up-to-date uv must come out as
 * [UvSelfUpdateResult.NoVersionChange], so the sample outputs that name a single version matter as much as the one
 * that names two.
 */
class UvSelfUpdateParserTest {
  @Test
  fun testUpdateAvailable() {
    val stdout = """
      info: Checking for updates...
      Would update uv from v0.12.1 to v0.12.18
    """.trimIndent()
    assertEquals(UvSelfUpdateResult.VersionChange("0.12.1", "0.12.18"), parseUvSelfUpdate(stdout))
  }

  /** `--quiet` drops uv's `info:` progress line, leaving the outcome on its own. */
  @Test
  fun testUpdateAvailableQuiet() {
    assertEquals(
      UvSelfUpdateResult.VersionChange("0.12.1", "0.12.18"),
      parseUvSelfUpdate("Would update uv from v0.12.1 to v0.12.18"),
    )
  }

  /** uv pads its output with a blank line, and on Windows the lines carry a `\r`. */
  @Test
  fun testUpdateAvailableWithPaddingAndCarriageReturns() {
    assertEquals(
      UvSelfUpdateResult.VersionChange("0.12.1", "0.12.18"),
      parseUvSelfUpdate("\r\ninfo: Checking for updates...\r\nWould update uv from v0.12.1 to v0.12.18\r\n"),
    )
  }

  /** A performed update reads the same as a previewed one, apart from the tense and uv's exclamation mark. */
  @Test
  fun testUpdatePerformed() {
    assertEquals(
      UvSelfUpdateResult.VersionChange("0.12.1", "0.12.18"),
      parseUvSelfUpdate("success: Upgraded uv from v0.12.1 to v0.12.18!"),
    )
  }

  /** What uv answers for an explicit target version it is already on. */
  @Test
  fun testAlreadyOnRequestedVersion() {
    val stdout = """
      info: Checking for updates...
      success: You're already on version v0.12.1 of uv.
    """.trimIndent()
    assertEquals(UvSelfUpdateResult.NoVersionChange, parseUvSelfUpdate(stdout))
  }

  /**
   * A single-version "up to date" line must never read as a version change. uv has phrased this more than one way
   * across releases, so the parser recognizes the two-version shape rather than trying to match every wording.
   */
  @Test
  fun testAlreadyLatest() {
    assertEquals(
      UvSelfUpdateResult.NoVersionChange,
      parseUvSelfUpdate("success: You're on the latest version of uv (v0.12.18)."),
    )
  }

  @Test
  fun testNoOutput() {
    assertEquals(UvSelfUpdateResult.NoVersionChange, parseUvSelfUpdate(""))
  }

  /**
   * The stream the outcome is read from. `uv self update` is the odd one out among the uv commands here: it writes
   * its whole report to stderr and leaves stdout empty, so a transformer reading stdout sees nothing and reports an
   * up-to-date uv however far behind it is. Captured from a real uv by redirecting the two streams separately.
   */
  @Test
  fun testReportIsReadFromStderr() {
    val output = EelProcessExecutionResult(
      exitCode = 0,
      stdout = ByteArray(0),
      stderr = "info: Checking for updates...\nWould update uv from v0.12.1 to v0.12.18\n".toByteArray(),
    )
    assertEquals(UvSelfUpdateResult.VersionChange("0.12.1", "0.12.18"), SELF_UPDATE_TRANSFORMER(output).getOrNull())
  }

  /**
   * uv exits non-zero when it cannot update itself at all — a uv installed by pip, Homebrew or apt rather than by
   * the standalone installer. That must fail rather than read as up to date, so its stderr reaches the user.
   */
  @Test
  fun testRefusalFails() {
    val output = EelProcessExecutionResult(
      exitCode = 2,
      stdout = ByteArray(0),
      stderr = "error: Self-update is only available for uv binaries installed via the standalone installation scripts.".toByteArray(),
    )
    assertNull(SELF_UPDATE_TRANSFORMER(output).getOrNull())
  }

  /** A pre-release target is not a dotted triple; uv would still print it, so it must survive the parse. */
  @Test
  fun testPreReleaseTarget() {
    assertEquals(
      UvSelfUpdateResult.VersionChange("0.12.18", "0.13.0a1"),
      parseUvSelfUpdate("Would update uv from v0.12.18 to v0.13.0a1"),
    )
  }
}
