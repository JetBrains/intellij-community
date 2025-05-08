// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.quickFixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.ui.pyModalBlocking

/**
 * A quick-fix for setting up the poetry for the module of the current PSI element.
 */
class PoetryAssociationQuickFix: LocalQuickFix {
  private val quickFixName = PyBundle.message("python.sdk.poetry.quickfix.use.pipenv.name")

  override fun getFamilyName() = quickFixName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor): Unit = pyModalBlocking {
    val element = descriptor.psiElement ?: return@pyModalBlocking
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return@pyModalBlocking
    module.pythonSdk?.setAssociationToModule(module)
  }
}