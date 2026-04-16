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
   */
  suspend fun install(name: String): PyResult<String> {
    return executeAndHandleErrors("install", name, transformer = ZeroCodeStdoutTransformer)
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
  suspend fun list(showVersionSpecifiers: Boolean? = null, showPaths: Boolean? = null): PyResult<String> {
    val options = listOf(
      showVersionSpecifiers to "--show-version-specifiers",
      showPaths to "--show-paths",
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
