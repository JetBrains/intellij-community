// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.python.community.execService.Args
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import io.github.z4kn4fein.semver.Version
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface UvCli<P : PathHolder> {
  val fileSystem: FileSystem<P>
  /**
   * [workingDir] is the directory uv runs in. It is `null` for a command that needs none, such as a query of the uv
   * Python list. It must be absolute, because a relative path names no directory on the machine that runs uv.
   */
  suspend fun runUv(workingDir: Path?, venvPath: P?, canChangeTomlOrLock: Boolean, vararg args: String): PyResult<String>

  /**
   * Runs uv with arguments that may name local files, so that a file reaches the machine uv runs on rather than being
   * passed as a path that means nothing there.
   */
  suspend fun runUv(workingDir: Path?, venvPath: P?, canChangeTomlOrLock: Boolean, args: Args): PyResult<String>
}

@ApiStatus.Internal
interface UvLowLevel<P : PathHolder> {
  /** `uv init --bare --no-project` in the working directory: a `pyproject.toml` named after the directory, nothing else. */
  suspend fun initProject(version: Version?): PyResult<Unit>

  /** `uv venv`, after [initProject] when [init] is set. Returns the Python binary of the new environment. */
  suspend fun initializeEnvironment(
    init: Boolean,
    version: Version?,
    clearExisting: Boolean = false,
    inheritSitePackages: Boolean = false,
  ): PyResult<P>

  suspend fun listUvPythons(): PyResult<Set<Path>>
  suspend fun listSupportedPythonVersions(versionRequest: String? = null): PyResult<List<Version>>

  /**
  * Manage project dependencies by adding/removing them to the project along side installation
  */
  suspend fun addDependency(pyPackages: PythonPackageInstallRequest, options: List<String>, workspaceMember: PyWorkspaceMember? = null, dependencyGroup: PyDependencyGroup? = null): PyResult<Unit>
  suspend fun removeDependencies(pyPackages: Array<out String>, workspaceMember: PyWorkspaceMember? = null, dependencyGroup: PyDependencyGroup? = null): PyResult<Unit>

  /**
   * Managing environment packages directly w/o depending or changing the project
   */
  suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>
  suspend fun uninstallPackages(pyPackages: Array<out String>): PyResult<Unit>

  /** `uv pip install -r <file>`. Adds what the file names and removes nothing. */
  suspend fun installRequirements(requirementsFile: Path): PyResult<Unit>

  /** `uv pip sync <file>`. Makes the environment match the file, so it removes what the file does not name. */
  suspend fun syncRequirements(requirementsFile: Path): PyResult<Unit>

  suspend fun listPackages(): PyResult<List<PythonPackage>>
  suspend fun listOutdatedPackages(): PyResult<List<PythonOutdatedPackage>>
  suspend fun listPackageRequirements(name: PythonPackage): PyResult<List<PyPackageName>>
  suspend fun listProjectStructureTree(): PyResult<String>
  suspend fun listAllPackagesTree(): PyResult<String>

  suspend fun isProjectSynced(inexact: Boolean): PyResult<Boolean>
  suspend fun isScriptSynced(inexact: Boolean, scriptPath: Path): PyResult<ScriptSyncCheckResult>

  /**
   * Brings the environment in line with the project's lock file.
   *
   * [python] pins the interpreter to sync with. Left null, uv picks one itself from `.python-version` and
   * `requires-python` — and where that disagrees with the environment already there, uv deletes it and builds another.
   * So a caller that has just built an environment on a chosen Python must name it here, or sync undoes that choice.
   */
  suspend fun sync(python: Version? = null): PyResult<String>
  suspend fun lock(): PyResult<String>

  /**
   * Materializes the PEP 723 environment of [scriptPath] and reports it, so that the script can then be run by that
   * environment's interpreter. Fails when the script carries no metadata block or uv is too old to report the
   * environment.
   */
  suspend fun syncScript(scriptPath: Path): PyResult<UvScriptEnvironment>
}

/**
 * The environment uv maintains for a PEP 723 script. [pythonPath] is the interpreter to run the script with; uv
 * chooses it from the script's `requires-python`, so it need not be the interpreter of the configured SDK.
 *
 * Both are paths on the machine uv ran on, kept as reported: parsing them locally would mangle a target path whose
 * separators are not the host's.
 */
@ApiStatus.Internal
data class UvScriptEnvironment(val path: String, val pythonPath: String)

@ApiStatus.Internal
sealed class ScriptSyncCheckResult {
  data object Synced : ScriptSyncCheckResult()
  data object NotSynced : ScriptSyncCheckResult()
  data object NoInlineMetadata : ScriptSyncCheckResult()
}