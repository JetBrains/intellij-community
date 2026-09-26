// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.pytools.backend.InstalledInfo
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.GenericPyToolManager
import com.intellij.python.pytools.backend.GenericPyToolManagerProvider
import com.intellij.python.pytools.backend.getToolVersion
import com.intellij.python.uv.backend.cli.uv.UvSelfUpdateResult
import com.intellij.python.uv.backend.runtime.createUvToolRuntime
import com.intellij.python.uv.backend.runtime.uvCli
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.intellij.python.sdk.backend.PySdkBundle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = Logger.getInstance(UvToolManagerProvider::class.java)

/**
 * Provides a [UvToolManager] when a local `uv` is available for the target environment. Registered
 * `order="first"` so uv is preferred over the pip fallback.
 */
@ApiStatus.Internal
class UvToolManagerProvider : GenericPyToolManagerProvider {
  override suspend fun forEel(eel: EelApi): GenericPyToolManager? {
    val uv = PyExecutableCache.getInstance().get(eel.descriptor, UvPyTool.getInstance()) ?: return null
    return UvToolManager(eel.toFileSystem(), uv)
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
) : GenericPyToolManager {
  /**
   * uv itself needs no install: this manager exists only because uv resolved. Installing it as one of uv's own
   * tools would shadow the resolved uv with a second copy in uv's bin directory.
   */
  override suspend fun install(tool: PyTool): PyResult<Path> =
    if (tool == UvPyTool.getInstance()) Result.success(uv) else run(tool, reinstall = false)

  /**
   * uv itself updates itself. [list] reports uv only while `uv self update` can act, so a uv that came from a
   * package manager is never routed here — its own backend upgrades it.
   */
  override suspend fun upgrade(tool: PyTool): PyResult<Path> =
    if (tool == UvPyTool.getInstance()) selfUpdate() else run(tool, reinstall = true)

  private suspend fun selfUpdate(): PyResult<Path> {
    createUvToolRuntime(uv).uvCli().self().update().getOr { return it }
    // `uv self update` replaces the binary behind the same path, so the executable does not move.
    return Result.success(uv)
  }

  /**
   * The tools among [tools] that uv manages: the ones uv installed, plus uv itself when uv is uv's to manage. uv
   * answers for all of them in the same two calls, so the list only narrows the result — except for uv itself, whose
   * answer costs a call of its own and is skipped when uv was not asked about.
   */
  override suspend fun list(tools: Collection<PyTool>): Map<PyTool, InstalledInfo> {
    val requested = tools.toSet()
    val own = if (UvPyTool.getInstance() in requested) uvItself() else emptyMap()
    return uvInstalledTools().filterKeys { it in requested } + own
  }

  /**
   * All uv-installed tools, from `uv tool list --show-paths`, with latest versions overlaid from
   * `uv tool list --outdated` (uv 0.10.10+; on older uv that call fails and every tool is reported as
   * up to date). Tools uv installed that the IDE does not know as a [PyTool], or whose executable path
   * is missing, are skipped.
   */
  private suspend fun uvInstalledTools(): Map<PyTool, InstalledInfo> {
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

  /**
   * uv's own entry, which `uv tool list` never reports: uv installs tools, it is not one of them. The installed
   * version comes from `uv --version` and the one an upgrade would reach from `uv self update --dry-run`, which
   * reports no target when uv is already current.
   *
   * A failed dry run means uv cannot update itself — what it answers when uv came from a package manager rather
   * than the standalone installer. uv is then not uv's to manage, so nothing is reported here and the caller falls
   * through to the backend that does manage it.
   */
  private suspend fun uvItself(): Map<PyTool, InstalledInfo> {
    val uvTool = UvPyTool.getInstance()
    val installedVersion = BinOnEel(uv).getToolVersion(uvTool.packageName.name).getOrNull()?.value ?: return emptyMap()
    val latestVersion = when (val update = createUvToolRuntime(uv).uvCli().self().update(dryRun = true).getOrNull()) {
      is UvSelfUpdateResult.VersionChange -> update.targetVersion
      UvSelfUpdateResult.NoVersionChange -> installedVersion
      null -> return emptyMap()
    }
    return mapOf(uvTool to InstalledInfo(path = uv, installedVersion = installedVersion, latestVersion = latestVersion))
  }

  private suspend fun run(tool: PyTool, reinstall: Boolean): PyResult<Path> {
    val name = tool.packageName.name
    val uvTool = createUvToolRuntime(uv).uvCli().tool()
    uvTool.install(name, reinstall = reinstall).getOr { return it }
    // uv installs launchers into its own bin dir (`uv tool dir --bin`), which is not on PATH by default. Add it
    // so the tool is also runnable from the user's shell; best-effort, since the IDE uses the resolved path below
    // regardless (PY-91493).
    uvTool.updateShell().orLogException(LOG)
    // Resolve the executable from uv's own records rather than re-detecting it on PATH: the current process does
    // not see the freshly added PATH entry, so a PATH lookup would spuriously fail with "cannot find executable".
    val executable = uvTool.list(showPaths = true).getOrNull()
                       ?.firstOrNull { PyTool.findByPackageName(it.name) == tool }
                       ?.let { it.executables[name] ?: it.executables.values.firstOrNull() }
                     ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", name, fileSystem.userReadableName))
    return Result.success(executable)
  }
}
