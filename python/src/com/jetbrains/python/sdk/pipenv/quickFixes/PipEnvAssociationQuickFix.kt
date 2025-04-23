// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv.quickFixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule

/**
 * A quick-fix for setting up the pipenv for the module of the current PSI element.
 */
class PipEnvAssociationQuickFix : LocalQuickFix {
  private val quickFixName = PyBundle.message("python.sdk.pipenv.quickfix.use.pipenv.name")

  override fun getFamilyName() = quickFixName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    module.pythonSdk?.setAssociationToModule(module)
  }
}