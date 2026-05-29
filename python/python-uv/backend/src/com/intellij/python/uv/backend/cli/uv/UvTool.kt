// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path

/**
 * Run and install commands provided by Python packages
 */
@Suppress("unused")
class UvTool(runtime: PyToolRuntime) : UvCommand("tool", runtime) {
  /**
   * Run a command provided by a Python package
   */
  suspend fun run(): PyResult<Unit> = TODO()

  /**
   * Install commands provided by a Python package.
   *
   * @param name Package name (or PEP 508 spec) to install persistently.
   * @param reinstall Pass `true` to force a reinstall even when the tool is already installed.
   *  Useful for breaking out of a previously pinned install (uv leaves the existing entry alone
   *  otherwise, and `uv tool upgrade` is bounded by the original constraints so it cannot help).
   */
  suspend fun install(name: String, reinstall: Boolean? = null): PyResult<String> {
    val options = listOf(reinstall to "--reinstall").makeOptions()
    return executeAndHandleErrors("install", name, *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Upgrade an installed tool to the latest compatible version.
   *
   * uv has no preview mode for `tool upgrade` (no `--dry-run`), so this always performs the upgrade.
   */
  suspend fun upgrade(name: String): PyResult<String> {
    return executeAndHandleErrors("upgrade", name, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * List installed tools
   */
  suspend fun list(showVersionSpecifiers: Boolean? = null, showPaths: Boolean? = null, outdated: Boolean? = null): PyResult<String> {
    val options = listOf(
      showVersionSpecifiers to "--show-version-specifiers",
      showPaths to "--show-paths",
      outdated to "--outdated",
    ).makeOptions()
    return executeAndHandleErrors("list", *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Parsed form of `uv tool list --show-paths`. Each header line `name vX.Y.Z (path/to/env)` becomes one entry;
   * the `- executable` lines under each header are ignored (they are re-derivable from the env directory).
   */
  suspend fun listInstalled(): PyResult<List<UvInstalledTool>> {
    val stdout = list(showPaths = true).getOr { return it }
    val headerRegex = Regex("""^(\S+) v(\S+) \((.+)\)$""")
    val tools = stdout.lineSequence()
      .mapNotNull { headerRegex.matchEntire(it.trim()) }
      .map { UvInstalledTool(name = it.groupValues[1], version = it.groupValues[2], envPath = Path.of(it.groupValues[3])) }
      .toList()
    return Result.success(tools)
  }

  /**
   * Parsed form of `uv tool list --outdated` (available since uv 0.10.10). Each header line
   * `name vCURRENT [latest: NEWER]` becomes one entry; subordinate `- executable` lines are ignored.
   * Returns only tools that have a newer release available.
   */
  suspend fun listOutdated(): PyResult<List<UvOutdatedTool>> {
    val stdout = list(outdated = true).getOr { return it }
    val headerRegex = Regex("""^(\S+) v(\S+) \[latest:\s*(\S+)]$""")
    val tools = stdout.lineSequence()
      .mapNotNull { headerRegex.matchEntire(it.trim()) }
      .map { UvOutdatedTool(name = it.groupValues[1], currentVersion = it.groupValues[2], latestVersion = it.groupValues[3]) }
      .toList()
    return Result.success(tools)
  }

  /**
   * Uninstall a tool
   */
  suspend fun uninstall(): PyResult<Unit> = TODO()

  /**
   * Ensure that the tool executable directory is on the PATH
   */
  suspend fun updateShell(): PyResult<Unit> = TODO()

  /**
   * Show the path to the uv tools directory
   */
  suspend fun dir(bin: Boolean? = null): PyResult<String> {
    val options = listOf(bin to "--bin").makeOptions()
    return executeAndHandleErrors("dir", *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Convenience wrapper over [dir] with `--bin` that returns a parsed [Path].
   */
  suspend fun binDir(): PyResult<Path> {
    val output = dir(bin = true).getOr { return it }
    return Result.success(Path.of(output.trim()))
  }
}

data class UvInstalledTool(val name: String, val version: String, val envPath: Path)

data class UvOutdatedTool(val name: String, val currentVersion: String, val latestVersion: String)
