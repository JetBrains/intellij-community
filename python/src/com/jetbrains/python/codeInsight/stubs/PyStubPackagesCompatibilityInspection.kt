// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stubs.visitors.PyIncompatibleStubVisitor
import com.jetbrains.python.inspections.PyInspection

class PyStubPackagesCompatibilityInspection : PyInspection() {
  @Suppress("MemberVisibilityCanBePrivate")
  var ignoredStubPackages: MutableList<String> = mutableListOf()

  override fun getOptionsPane(): OptPane =
    OptPane.pane(OptPane.stringList("ignoredStubPackages", PyPsiBundle.message("INSP.stub.packages.compatibility.ignored.packages.label")))

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return PyIncompatibleStubVisitor(ignoredStubPackages, holder, session)
  }
}