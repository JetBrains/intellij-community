// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PipBasedPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override suspend fun installPackage(specification: PythonPackageSpecification) {
    withContext(Dispatchers.IO) {
      runPackagingTool("install", specification.buildInstallationString(), PyBundle.message("python.packaging.install.progress", specification.name))
      reloadPackages()
    }
  }

  override suspend fun uninstallPackage(pkg: PythonPackage) {
    withContext(Dispatchers.IO) {
      runPackagingTool("uninstall", listOf(pkg.name), PyBundle.message("python.packaging.uninstall.progress", pkg.name))
      reloadPackages()
    }
  }
}