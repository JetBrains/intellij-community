// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.visitors

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil

internal abstract class PyStubVisitor(
  holder: ProblemsHolder,
  session: LocalInspectionToolSession,
) : PyInspectionVisitor(holder, getContext(session)) {
  override fun visitPyFile(file: PyFile) {
    val module = ModuleUtilCore.findModuleForFile(file) ?: return
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return

    val importedPackages = loadImportedPackages(file).ifEmpty { null } ?: return
    val packageManager = PythonPackageManager.forSdk(module.project, sdk)

    checkImports(file, importedPackages, packageManager)
  }

  protected abstract fun checkImports(file: PyFile, importedPackages: Set<String>, packageManager: PythonPackageManager)

  private fun loadImportedPackages(file: PyFile): Set<String> {
    val sources = mutableSetOf<String>()
    file.fromImports.mapNotNullTo(sources) { it.importSourceQName?.firstComponent }
    file.importTargets.mapNotNullTo(sources) { it.importedQName?.firstComponent }


    val importedPackages = sources.map {
      PyPsiPackageUtil.moduleToPackageName(it, it)
    }.toSet()

    return importedPackages.toSet()
  }
}