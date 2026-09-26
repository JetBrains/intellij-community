// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.pytools.backend.runtime.cliArg
import com.intellij.python.pytools.backend.runtime.cliArgs
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Manage the uv executable
 */
@Suppress("unused")
class UvSelf(runtime: PyToolRuntime) : UvCommand("self", runtime) {
  /**
   * Update uv to the latest released version.
   *
   * @param targetVersion Update to this version instead of the latest release.
   * @param dryRun Passes `--dry-run`: report what the update would do and change nothing.
   */
  suspend fun update(targetVersion: String? = null, dryRun: Boolean = false): PyResult<UvSelfUpdateResult> {
    val arguments = cliArgs(
      cliArg(targetVersion),
      cliArg("--dry-run".takeIf { dryRun }),
    )
    return executeAndHandleErrors("update", *arguments, transformer = SELF_UPDATE_TRANSFORMER)
  }

  /**
   * Display uv's version
   */
  suspend fun version(short: Boolean? = null): PyResult<String> {
    val arguments = cliArgs(
      cliArg("--short".takeIf { short == true }),
    )
    return executeAndHandleErrors("version", *arguments, transformer = ZeroCodeStdoutTransformer)
  }
}

/**
 * What `uv self update` reported. A failure of the call is something else entirely: uv answers one when it cannot
 * update itself at all, which is the case for a uv installed by a package manager rather than by the standalone
 * installer.
 */
sealed interface UvSelfUpdateResult {
  /**
   * uv reported a version change: it moved from [fromVersion] to [targetVersion], or would have under `--dry-run`.
   */
  data class VersionChange(val fromVersion: String, val targetVersion: String) : UvSelfUpdateResult

  /** uv reported no version change, which is what it answers when it is already on the newest release it offers. */
  data object NoVersionChange : UvSelfUpdateResult
}

/**
 * `uv self update` reports on stderr and leaves stdout empty, unlike every other uv command here, so the outcome is
 * parsed from stderr. The exit code still says whether the command worked: uv exits non-zero when it cannot update
 * itself at all, and the failure carries its stderr — the refusal names the installation method to use instead, so
 * the user needs to read it.
 */
internal val SELF_UPDATE_TRANSFORMER: ProcessOutputTransformer<UvSelfUpdateResult> = { output ->
  if (output.exitCode != 0) Result.failure(null)
  else Result.success(parseUvSelfUpdate(output.stderrString))
}

/**
 * Parses `uv self update`.
 *
 * uv states a version change as two versions on one line (`Would update uv from v0.12.1 to v0.12.18`), and an
 * up-to-date install as a line naming a single version (`You're already on version v0.12.18 of uv.`). Only the
 * two-version shape is recognized, so the up-to-date wording — which uv has wound up phrasing more than one way
 * across releases — can never be read as a version change.
 *
 * Separate from the call so it can be tested against captured output.
 */
internal fun parseUvSelfUpdate(stderr: String): UvSelfUpdateResult {
  val match = stderr.lineSequence()
                .map { it.trim() }
                .firstNotNullOfOrNull { VERSION_CHANGE_REGEX.find(it) }
              ?: return UvSelfUpdateResult.NoVersionChange
  return UvSelfUpdateResult.VersionChange(fromVersion = match.groupValues[1], targetVersion = match.groupValues[2])
}

private val VERSION_CHANGE_REGEX: Regex = Regex("""\bfrom v(\S+) to v(\S+?)[.!]?$""")
