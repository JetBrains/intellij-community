// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal interface PythonPackageManagerEngine {
  @ApiStatus.Internal
  suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>

  @ApiStatus.Internal
  suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit>

  @ApiStatus.Internal
  suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit>

  @ApiStatus.Internal
  suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>>

  @ApiStatus.Internal
  suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>>
}