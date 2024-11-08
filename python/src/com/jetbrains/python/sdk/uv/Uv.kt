// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import java.nio.file.Path

interface UvCli {
  suspend fun runUv(workingDir: Path, vararg args: String): Result<String>
}

interface UvLowLevel {
  suspend fun initializeEnvironment(init: Boolean, python: Path?): Result<Path>

  suspend fun listPackages(): Result<List<PythonPackage>>
  suspend fun listOutdatedPackages(): Result<List<PythonOutdatedPackage>>

  suspend fun installPackage(name: PythonPackageSpecification, options: List<String>): Result<Unit>
  suspend fun uninstallPackage(name: PythonPackage): Result<Unit>
}