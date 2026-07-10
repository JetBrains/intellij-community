// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend

import com.intellij.platform.eel.EelApi
import com.intellij.python.pytools.InstalledInfo
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolManager
import com.intellij.python.pytools.PyToolManagerProvider
import com.intellij.python.uv.backend.runtime.createUvToolRuntime
import com.intellij.python.uv.backend.runtime.uvCli
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.impl.PySdkBundle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Provides a [UvToolManager] when a local `uv` is available for the target environment. Registered
 * `order="first"` so uv is preferred over the pip fallback.
 */
@ApiStatus.Internal
class UvToolManagerProvider : PyToolManagerProvider {
  override suspend fun forEel(eel: EelApi): PyToolManager? {
    val fileSystem = eel.toFileSystem()
    val uv = UV_TOOL.getToolExecutable(fileSystem, null)?.path ?: return null
    return UvToolManager(fileSystem, uv)
  }
}

/**
 * `uv tool` manager bound to a resolved `uv` executable and its filesystem. Install/upgrade use
 * `uv tool install [--reinstall]` (isolated, always-latest env); `--reinstall` drops any prior pin so
 * an upgrade resolves to the latest release rather than staying on the originally-installed spec.
 */
private class UvToolManager(
  private val fileSystem: FileSystem<PathHolder.Eel>,
  private val uv: Path,
) : PyToolManager {
  override suspend fun install(tool: PyTool): PyResult<Path> = run(tool, reinstall = false)

  override suspend fun upgrade(tool: PyTool): PyResult<Path> = run(tool, reinstall = true)

  /**
   * All uv-installed tools, from `uv tool list --show-paths`, with latest versions overlaid from
   * `uv tool list --outdated` (uv 0.10.10+; on older uv that call fails and every tool is reported as
   * up to date). Tools uv installed that the IDE does not know as a [PyTool], or whose executable path
   * is missing, are skipped.
   */
  override suspend fun list(): Map<PyTool, InstalledInfo> {
    val tool = createUvToolRuntime(uv).uvCli().tool()
    val installed = tool.list(showPaths = true).getOr { return emptyMap() }
    val latestByName = tool.list(outdated = true).getOrNull().orEmpty()
      .mapNotNull { outdated -> outdated.latestVersion?.let { outdated.name to it } }
      .toMap()
    return installed.mapNotNull { uvTool ->
      val pyTool = PyTool.findByPackageName(uvTool.name) ?: return@mapNotNull null
      // A tool may expose several entry points (e.g. pyright, pyright-langserver, …); prefer the one
      // named after the tool, otherwise take the first uv reported.
      val executablePath = uvTool.executables[uvTool.name] ?: uvTool.executables.values.firstOrNull() ?: return@mapNotNull null
      // `uv tool list --outdated` omits up-to-date tools, so absence means latest == installed.
      val latestVersion = latestByName[uvTool.name] ?: uvTool.version
      pyTool to InstalledInfo(path = executablePath, installedVersion = uvTool.version, latestVersion = latestVersion)
    }.toMap()
  }

  private suspend fun run(tool: PyTool, reinstall: Boolean): PyResult<Path> {
    createUvToolRuntime(uv).uvCli().tool().install(tool.packageName.name, reinstall = reinstall).getOr { return it }
    val executable = fileSystem.detectTool(tool.packageName.name)
                     ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", tool.packageName.name, fileSystem.userReadableName))
    return Result.success(executable.path)
  }
}
