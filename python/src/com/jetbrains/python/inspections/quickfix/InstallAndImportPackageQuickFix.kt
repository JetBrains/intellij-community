// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import org.jetbrains.annotations.Nls

internal class InstallAndImportPackageQuickFix(
  private val packageName: String,
  private val importAlias: String?,
) : LocalQuickFix {

  override fun getName(): @Nls String = PyPsiBundle.message("QFIX.NAME.install.and.import.package", packageName)

  override fun getFamilyName(): @Nls String = PyPsiBundle.message("QFIX.install.and.import.package")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) =
    InstallPackageQuickFix(packageName).applyFix(project, descriptor)

  fun onSuccess(descriptor: ProblemDescriptor) {
    runWriteActionWithDescriptor(descriptor.psiElement)
  }

  private fun runWriteActionWithDescriptor(element: PsiElement) =
    WriteCommandAction.writeCommandAction(element.project)
      .withName(PyPsiBundle.message("INSP.package.requirements.add.import"))
      .withGroupId(GROUP_ID)
      .run<RuntimeException> {
        addImportToFile(element)
      }

  private fun addImportToFile(element: PsiElement) =
    AddImportHelper.addImportStatement(
      element.containingFile,
      packageName,
      importAlias,
      AddImportHelper.ImportPriority.THIRD_PARTY,
      element
    )

  companion object {
    private const val GROUP_ID = "Add import"
  }
}