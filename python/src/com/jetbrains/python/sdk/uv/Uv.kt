// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import io.github.z4kn4fein.semver.Version
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface UvCli {
  suspend fun runUv(workingDir: Path, vararg args: String): PyResult<String>
}

@ApiStatus.Internal
interface UvLowLevel {
  suspend fun initializeEnvironment(init: Boolean, version: Version?): PyResult<Path>

  suspend fun listUvPythons(): PyResult<Set<Path>>
  suspend fun listSupportedPythonVersions(versionRequest: String? = null): PyResult<List<Version>>

  /**
  * Manage project dependencies by adding/removing them to the project along side installation
  */
  suspend fun addDependency(pyPackages: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>
  suspend fun removeDependencies(pyPackages: Array<out String>): PyResult<Unit>

  /**
   * Managing environment packages directly w/o depending or changing the project
   */
  suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>
  suspend fun uninstallPackages(pyPackages: Array<out String>): PyResult<Unit>

  suspend fun listPackages(): PyResult<List<PythonPackage>>
  suspend fun listOutdatedPackages(): PyResult<List<PythonOutdatedPackage>>
  suspend fun listTopLevelPackages(): PyResult<List<PythonPackage>>
  suspend fun listPackageRequirements(name: PythonPackage): PyResult<List<PyPackageName>>
  suspend fun listPackageRequirementsTree(name: PythonPackage): PyResult<String>

  suspend fun isProjectSynced(inexact: Boolean): PyResult<Boolean>
  suspend fun isScriptSynced(inexact: Boolean, scriptPath: Path): PyResult<ScriptSyncCheckResult>

  suspend fun sync(): PyResult<String>
  suspend fun lock(): PyResult<String>
}

@ApiStatus.Internal
sealed class ScriptSyncCheckResult {
  data object Synced : ScriptSyncCheckResult()
  data object NotSynced : ScriptSyncCheckResult()
  data object NoInlineMetadata : ScriptSyncCheckResult()
}