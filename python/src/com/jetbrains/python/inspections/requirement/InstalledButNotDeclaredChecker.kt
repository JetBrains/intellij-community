// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.requirement

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.toRequirements
import com.jetbrains.python.packaging.pip.PyPiPackageCache

internal class InstalledButNotDeclaredChecker(
  private val ignoredPackages: Collection<PyPackageName>,
  private val declared: List<PythonPackage>,
) {
  fun getUndeclaredPackageName(importedPyModule: String): PyPackageName? {
    val packageName = PyPackageName.from(PyPsiPackageUtil.moduleToPackageName(importedPyModule))
    if (isIgnoredOrStandardPackage(packageName, importedPyModule))
      return null

    val pyPiCacheService = ApplicationManager.getApplication().service<PyPiPackageCache>()

    if (packageName.name !in pyPiCacheService)
      return null

    if (declared.toRequirements().any { it.packageName == packageName }) {
      return null
    }

    return packageName
  }

  private fun isIgnoredOrStandardPackage(packageName: PyPackageName, rawModuleName: String): Boolean =
    ignoredPackages.contains(packageName) ||
    packageName.name == PyPackageUtil.SETUPTOOLS ||
    PyStdlibUtil.getPackages()?.contains(rawModuleName) == true
}