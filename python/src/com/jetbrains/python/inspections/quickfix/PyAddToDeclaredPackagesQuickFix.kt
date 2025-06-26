// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.dependencies.PythonDependenciesManager
import org.jetbrains.annotations.Nls

class PyAddToDeclaredPackagesQuickFix(
  private val manager: PythonDependenciesManager,
  val packageName: String,
) : LocalQuickFix {
  override fun startInWriteAction(): Boolean = false

  override fun getFamilyName(): @Nls String = PyPsiBundle.message("QFIX.add.imported.package.to.declared.packages", packageName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    manager.addDependency(packageName)
  }
}