// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModuleAsync

internal class UvAssociationQuickFix : LocalQuickFix {
  private val quickFixName = PyBundle.message("python.sdk.quickfix.use.uv.name")

  override fun getFamilyName() = quickFixName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    if (element == null) {
      return
    }

    val module = ModuleUtilCore.findModuleForPsiElement(element)
    if (module == null) {
      return
    }

    module.pythonSdk?.setAssociationToModuleAsync(module)
  }
}

