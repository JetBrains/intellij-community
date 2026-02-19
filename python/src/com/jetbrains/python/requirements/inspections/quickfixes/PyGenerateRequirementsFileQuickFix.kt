// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.syncWithImports

internal class PyGenerateRequirementsFileQuickFix(private val myModule: Module) : LocalQuickFix {
  override fun getFamilyName(): @IntentionFamilyName String {
    return PyPsiBundle.message("QFIX.add.imported.packages.to.requirements")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    syncWithImports(myModule)
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}