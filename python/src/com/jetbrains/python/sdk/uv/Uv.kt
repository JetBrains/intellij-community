// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface UvCli {
  suspend fun runUv(workingDir: Path, vararg args: String): PyResult<String>
}

@ApiStatus.Internal
interface UvLowLevel {
  suspend fun initializeEnvironment(init: Boolean, python: Path?): PyResult<Path>

  suspend fun listUvPythons(): PyResult<Set<Path>>

  /**
  * Manage project dependencies by adding/removing them to the project along side installation
  */
  suspend fun addDependency(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>
  suspend fun removeDependency(name: PythonPackage): PyResult<Unit>

  /**
   * Managing environment packages directly w/o depending or changing the project
   */
  suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>
  suspend fun uninstallPackage(name: PythonPackage): PyResult<Unit>

  suspend fun listPackages(): PyResult<List<PythonPackage>>
  suspend fun listOutdatedPackages(): PyResult<List<PythonOutdatedPackage>>

  suspend fun isProjectSynced(inexact: Boolean): PyResult<Boolean>
  suspend fun isScriptSynced(inexact: Boolean, scriptPath: Path): PyResult<ScriptSyncCheckResult>

  suspend fun sync(): Result<String>
  suspend fun lock(): Result<String>
}

@ApiStatus.Internal
sealed class ScriptSyncCheckResult {
  data object Synced : ScriptSyncCheckResult()
  data object NotSynced : ScriptSyncCheckResult()
  data object NoInlineMetadata : ScriptSyncCheckResult()
}