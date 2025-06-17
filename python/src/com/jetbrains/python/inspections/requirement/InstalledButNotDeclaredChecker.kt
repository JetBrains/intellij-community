// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.requirement

import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil
import com.jetbrains.python.packaging.PyPIPackageUtil.INSTANCE
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager

class InstalledButNotDeclaredChecker(val ignoredPackages: Collection<String>, val pythonPackageManager: PythonPackageManager) {
  fun getUndeclaredPackageName(importedPyModule: String): String? {
    val packageName = PyPsiPackageUtil.moduleToPackageName(importedPyModule)
    if (isIgnoredOrStandardPackage(importedPyModule))
      return null

    if (!INSTANCE.isInPyPI(packageName))
      return null


    val requirements = pythonPackageManager.getDependencyManager()?.getDependencies() ?: emptyList()
    if (requirements.any { it.name == packageName }) {
      return null
    }

    return packageName
  }


  private fun isIgnoredOrStandardPackage(packageName: String): Boolean =
    ignoredPackages.contains(packageName) ||
    packageName == PyPackageUtil.SETUPTOOLS ||
    PyStdlibUtil.getPackages()?.contains(packageName) == true
}