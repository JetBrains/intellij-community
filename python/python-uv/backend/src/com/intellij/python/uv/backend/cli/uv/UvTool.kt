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
  suspend fun install(name: String, reinstall: Boolean = false): PyResult<String> {
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
   * `uv tool list`, parsed into one [UvToolListResult] per installed tool. Optional fields are filled
   * according to the flags: [outdated] populates [UvToolListResult.latestVersion] for tools with a newer
   * release (uv 0.10.10+ lists only those), and [showPaths] populates [UvToolListResult.envPath] plus the
   * [UvToolListResult.executables] map (entry-point name -> path).
   *
   * uv prints a header line `name vVERSION [latest: NEWER]? (env/path)?` per tool, optionally followed
   * (with `--show-paths`) by one `- entrypoint (exe/path)` line per entry point (a tool may expose
   * several). Paths may contain spaces, so the parenthesized tails are matched greedily.
   */
  suspend fun list(showVersionSpecifiers: Boolean = false, showPaths: Boolean = false, outdated: Boolean = false): PyResult<List<UvToolListResult>> {
    val options = listOf(
      showVersionSpecifiers to "--show-version-specifiers",
      showPaths to "--show-paths",
      outdated to "--outdated",
    ).makeOptions()
    val stdout = executeAndHandleErrors("list", *options, transformer = ZeroCodeStdoutTransformer).getOr { return it }

    val headerRegex = Regex("""^(\S+) v(\S+)(?: \[latest:\s*(\S+)])?(?: \((.+)\))?$""")
    val entryPointRegex = Regex("""^-\s+(\S+)\s+\((.+)\)$""")

    val tools = mutableListOf<UvToolListResult>()
    var pending: UvToolListResult? = null
    val executables = linkedMapOf<String, Path>()

    fun flushPending() {
      val tool = pending ?: return
      tools += tool.copy(executables = executables.toMap())
      executables.clear()
      pending = null
    }

    for (rawLine in stdout.lineSequence()) {
      val line = rawLine.trim()
      val header = headerRegex.matchEntire(line)
      if (header != null) {
        flushPending()
        pending = UvToolListResult(
          name = header.groupValues[1],
          version = header.groupValues[2],
          latestVersion = header.groupValues[3].ifEmpty { null },
          envPath = header.groupValues[4].ifEmpty { null }?.let(Path::of),
        )
        continue
      }
      if (pending == null) continue
      val entryPoint = entryPointRegex.matchEntire(line) ?: continue
      executables[entryPoint.groupValues[1]] = Path.of(entryPoint.groupValues[2])
    }
    flushPending()
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
   * `uv tool dir` — the directory uv stores tools in, or (with [bin] = true) the directory their
   * executables are placed on `PATH`. uv prints a single path line, parsed here into a [Path].
   */
  suspend fun dir(bin: Boolean = false): PyResult<Path> {
    val options = listOf(bin to "--bin").makeOptions()
    val output = executeAndHandleErrors("dir", *options, transformer = ZeroCodeStdoutTransformer).getOr { return it }
    return Result.success(Path.of(output.trim()))
  }
}

/**
 * One entry of `uv tool list`. [name] and installed [version] are always present; [latestVersion] is
 * set only for outdated tools (`--outdated`), and [envPath] plus [executables] (each entry point's name
 * mapped to its path, insertion-ordered as uv printed them) only with `--show-paths`.
 */
data class UvToolListResult(
  val name: String,
  val version: String,
  val latestVersion: String? = null,
  val envPath: Path? = null,
  val executables: Map<String, Path> = emptyMap(),
)
