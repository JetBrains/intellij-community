// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.visitors

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stubs.checkers.PyStubsIncompatibilityChecker
import com.jetbrains.python.codeInsight.stubs.fixes.IgnoreStubCompatibilityQuickFix
import com.jetbrains.python.codeInsight.stubs.fixes.InstallStubQuickFix
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.PyFile

internal class PyIncompatibleStubVisitor(
  val ignoredStubPackages: MutableList<String>,
  holder: ProblemsHolder,
  val session: LocalInspectionToolSession,
) : PyStubVisitor(holder, session) {
  override fun checkImports(file: PyFile, importedPackages: Set<String>, packageManager: PythonPackageManager) {
    val checker = PyStubsIncompatibilityChecker.getInstance(project = packageManager.project)
    val cached = checker.getCached(packageManager.sdk)
    val stubs = cached.filter { it.stubRequirement.presentableText !in ignoredStubPackages && it.packageName.name in importedPackages }
    for (stub in stubs) {
      val message = PyPsiBundle.message("INSP.stub.packages.compatibility.incompatible.packages.message", stub.stubRequirement.name)
      registerProblem(file,
                      message,
                      InstallStubQuickFix(stub.stubRequirement, packageManager.sdk),
                      IgnoreStubCompatibilityQuickFix(stub.stubRequirement, ignoredStubPackages))
    }
  }
}