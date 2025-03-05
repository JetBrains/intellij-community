// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.quickFixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.requirement.RunningPackagingTasksListener
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.pythonSdk

/**
 * A quick-fix for installing packages specified in Pipfile.lock.
 */
class PoetryInstallQuickFix : LocalQuickFix {
  companion object {
    fun poetryInstall(project: Project, module: Module) {
      val sdk = module.pythonSdk ?: return
      if (!sdk.isPoetry) return
      // TODO: create UI
      val listener = RunningPackagingTasksListener(module)
      val ui = PyPackageManagerUI(project, sdk, listener)
      ui.install(null, listOf())
    }
  }

  override fun getFamilyName() = PyBundle.message("python.sdk.intention.family.name.install.requirements.from.poetry.lock")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    poetryInstall(project, module)
  }
}