// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv.quickFixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.sdk.pipenv.isPipEnv
import com.jetbrains.python.sdk.pythonSdk

/**
 * A quick-fix for installing packages specified in Pipfile.lock.
 */
internal class PipEnvInstallQuickFix : LocalQuickFix {
  companion object {
    fun pipEnvInstall(project: Project, module: Module) {
      val sdk = module.pythonSdk ?: return
      if (!sdk.isPipEnv) return
      val listener = PyPackageRequirementsInspection.RunningPackagingTasksListener(module)
      val ui = PyPackageManagerUI(project, sdk, listener)
      ui.install(null, listOf("--dev"))
    }
  }

  override fun getFamilyName() = PyBundle.message("python.sdk.install.requirements.from.pipenv.lock")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    pipEnvInstall(project, module)
  }
}