// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyEnvTestPackages")

package com.jetbrains.env

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.runBlocking

/**
 * Lists the installed packages of [interpreter] again, and returns them. A check that reads the package snapshot of
 * [PythonPackageManager], such as the detection of a test framework, then sees them.
 */
fun reloadInstalledPackages(project: Project, interpreter: PythonInterpreter): List<PythonPackage> = runBlocking {
  PythonPackageManager.forPythonInterpreter(project, interpreter).reloadPackages().getOrThrow()
}
