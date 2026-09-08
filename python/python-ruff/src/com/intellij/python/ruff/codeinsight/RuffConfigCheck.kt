// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.python.community.execService.Args
import com.intellij.python.ruff.RuffBundle
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.sdk.backend.executeToolInteractive
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

private val LOG = fileLogger()

/**
 * The name Ruff gets as the file to lint. Ruff must not find it. See [checkRuffConfig].
 *
 * The `.py` extension keeps Ruff on the path that reports a missing file.
 */
private const val MISSING_FILE_NAME = "not-a-real-file.py"

/** Ruff's code for a file it cannot open. */
private const val MISSING_FILE_CODE = "E902"

/** One error that Ruff reports for a config file it cannot load. [line] and [column] are 1-based. */
internal data class RuffConfigError(
  val line: Int,
  val column: Int,
  val width: Int,
  val message: String,
)

/**
 * Runs `ruff check` on [configText] and returns the config error that Ruff reports.
 * Returns `null` when Ruff loads the config.
 *
 * Ruff has no mode that validates a config alone. So the check gives Ruff one file to lint that does
 * not exist. Ruff loads the config first, so a broken config fails before the missing file matters.
 * The file must be absent on purpose. A real file would make Ruff report its lint problems too.
 *
 * [configText] goes to a temporary file in the environment of [workingDir], because Ruff runs there.
 * A path on the IDE host is unreadable for a Ruff that runs in WSL or Docker.
 * [configFileName] names that file, because Ruff reads the name to tell a `pyproject.toml` from a
 * `ruff.toml`.
 */
internal suspend fun checkRuffConfig(
  moduleOrProject: ModuleOrProject,
  configFileName: String,
  configText: String,
  workingDir: Path,
): PyResult<RuffConfigError?> {
  val eelApi = workingDir.getEelDescriptor().toEelApi()
  val tempDir = try {
    withContext(Dispatchers.IO) {
      val dir = eelApi.fs.createTemporaryDirectory()
        .prefix("ruff-config")
        .deleteOnExit(true)
        .getOrThrowFileSystemException()
        .asNioPath()
      (dir / configFileName).writeText(configText)
      dir
    }
  }
  catch (e: IOException) {
    LOG.warn("Cannot write the Ruff config to a temporary file", e)
    return Result.localizedError(RuffBundle.message("error.cannot.check.ruff.config"))
  }

  try {
    val output = moduleOrProject.executeToolInteractive(
      RuffPyTool.getInstance(),
      workingDir = workingDir,
      args = Args(
        "check",
        (tempDir / MISSING_FILE_NAME).asEelPath().toString(),
        "--config", (tempDir / configFileName).asEelPath().toString(),
      ),
      processSemiInteractiveFun = { _, processResult ->
        val result = processResult.await()
        // Ruff writes a config error to stderr and the missing-file report to stdout. The separator
        // must be a newline, because ERROR_PATTERN spans lines.
        Result.success("${result.stdout.decodeToString()}\n${result.stderr.decodeToString()}")
      },
    )
    return output.mapSuccess { parseRuffConfigOutput(it) }
  }
  finally {
    withContext(NonCancellable + Dispatchers.IO) {
      try {
        Files.deleteIfExists(tempDir / configFileName)
        Files.deleteIfExists(tempDir)
      }
      catch (e: IOException) {
        // deleteOnExit(true) still removes the directory, so this only delays the cleanup.
        LOG.warn("Cannot delete the temporary Ruff config directory $tempDir", e)
      }
    }
  }
}

/**
 * Reads the output of `ruff check` as run by [checkRuffConfig].
 * Returns `null` when Ruff loaded the config, and when the output has no shape this can read.
 */
internal fun parseRuffConfigOutput(output: String): RuffConfigError? {
  val match = ERROR_PATTERN.find(output)
  if (match != null) {
    return RuffConfigError(
      line = match.groups[1]!!.value.toInt(),
      column = match.groups[2]!!.value.toInt(),
      width = match.groups[3]!!.value.length,
      message = match.groups[4]!!.value,
    )
  }
  // Ruff got as far as the missing file, so it loaded the config. `E902` is a Ruff code, and Ruff
  // does not localize it. The OS message that follows it is localized.
  if (MISSING_FILE_CODE in output) return null
  LOG.info("Cannot read the `ruff check` output: $output")
  return null
}

/**
 * Ruff's report for a config file it cannot load. Ruff indents each `Cause` line by two spaces,
 * which the pattern spells as ` {2}` so it stays one space per space.
 */
private val ERROR_PATTERN = Regex(
  """
  ruff failed
   {2}(?:Cause: Failed to load configuration `.+`
   {2})?Cause: Failed to parse .+
   {2}Cause: TOML parse error at line (\d+), column (\d+)
   \s*\|
  \d+ \| .*
   \s*\|\s+(\^+)
  (.+)
  """.trimIndent()
)
