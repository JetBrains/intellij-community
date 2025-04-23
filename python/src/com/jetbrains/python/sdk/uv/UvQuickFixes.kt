// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.requirement.RunningPackagingTasksListener
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule

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

    module.pythonSdk?.setAssociationToModule(module)
  }
}

class UvInstallQuickFix : LocalQuickFix {
  companion object {
    fun uvInstall(project: Project, module: Module) {
      val sdk = module.pythonSdk
      if (sdk == null || !sdk.isUv) {
        return
      }

      val listener = RunningPackagingTasksListener(module)
      val ui = PyPackageManagerUI(project, sdk, listener)
      ui.install(null, listOf())
    }
  }

  override fun getFamilyName() = PyBundle.message("python.sdk.intention.family.name.install.requirements.from.uv.lock")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    if (element == null) {
      return
    }

    val module = ModuleUtilCore.findModuleForPsiElement(element)
    if (module == null) {
      return
    }

    uvInstall(project, module)
  }
}