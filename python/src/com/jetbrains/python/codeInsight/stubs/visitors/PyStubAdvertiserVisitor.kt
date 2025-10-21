// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.visitors

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.codeInsight.stubs.checkers.PyNotInstalledStubsChecker
import com.jetbrains.python.codeInsight.stubs.fixes.IgnoreStubAdvertiseQuickFix
import com.jetbrains.python.codeInsight.stubs.fixes.InstallStubQuickFix
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.PyFile


internal class PyStubAdvertiserVisitor(
  val ignoredPackages: MutableList<String>,
  holder: ProblemsHolder,
  val session: LocalInspectionToolSession,
) : PyStubVisitor(holder, session) {
  override fun checkImports(file: PyFile, importedPackages: Set<String>, packageManager: PythonPackageManager) {
    val checker = PyNotInstalledStubsChecker.getInstance(project = packageManager.project)
    val cached = checker.getCached(packageManager.sdk)
    val stubs = cached.filter { it.packageName.name in importedPackages && it.packageName.name !in ignoredPackages }
    for (stub in stubs) {
      val message = PyBundle.message("code.insight.type.hints.are.not.installed", stub.stubRequirement.name)
      registerProblem(file,
                      message,
                      InstallStubQuickFix(stub.stubRequirement, packageManager.sdk),
                      IgnoreStubAdvertiseQuickFix(stub.packageName, ignoredPackages))
    }
  }
}