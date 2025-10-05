// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stubs.visitors.PyStubAdvertiserVisitor
import com.jetbrains.python.inspections.PyInspection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyStubPackagesAdvertiser : PyInspection() {
  var ignoredPackages: MutableList<String> = mutableListOf()

  override fun getOptionsPane(): OptPane =
    OptPane.pane(OptPane.stringList("ignoredPackages", PyPsiBundle.message("INSP.stub.packages.compatibility.ignored.packages.label")))

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = PyStubAdvertiserVisitor(ignoredPackages, holder, session)
}