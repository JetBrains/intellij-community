// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface UvCli {
  suspend fun runUv(workingDir: Path, vararg args: String): PyExecResult<String>
}

@ApiStatus.Internal
interface UvLowLevel {
  suspend fun initializeEnvironment(init: Boolean, python: Path?): PyResult<Path>

  suspend fun listUvPythons(): PyResult<Set<Path>>

  /**
  * Manage project dependencies by adding/removing them to the project along side installation
  */
  suspend fun addDependency(pyPackages: PythonPackageInstallRequest, options: List<String>): PyExecResult<Unit>
  suspend fun removeDependencies(pyPackages: Array<out String>): PyExecResult<Unit>

  /**
   * Managing environment packages directly w/o depending or changing the project
   */
  suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyExecResult<Unit>
  suspend fun uninstallPackages(pyPackages: Array<out String>): PyExecResult<Unit>

  suspend fun listPackages(): PyExecResult<List<PythonPackage>>
  suspend fun listOutdatedPackages(): PyResult<List<PythonOutdatedPackage>>
  suspend fun listTopLevelPackages(): PyResult<List<PythonPackage>>
  suspend fun listPackageRequirements(name: PythonPackage): PyResult<List<NormalizedPythonPackageName>>

  suspend fun isProjectSynced(inexact: Boolean): PyExecResult<Boolean>
  suspend fun isScriptSynced(inexact: Boolean, scriptPath: Path): PyExecResult<ScriptSyncCheckResult>

  suspend fun sync(): PyExecResult<String>
  suspend fun lock(): PyExecResult<String>
}

@ApiStatus.Internal
sealed class ScriptSyncCheckResult {
  data object Synced : ScriptSyncCheckResult()
  data object NotSynced : ScriptSyncCheckResult()
  data object NoInlineMetadata : ScriptSyncCheckResult()
}