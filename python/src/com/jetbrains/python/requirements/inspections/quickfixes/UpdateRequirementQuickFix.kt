// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.management.ui.updatePackagesBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal class UpdateRequirementQuickFix(private val outdatedPackage: PythonOutdatedPackage) : LocalQuickFix, PriorityAction {
  override fun getFamilyName() = PyBundle.message("QFIX.NAME.update.requirement", outdatedPackage.name)
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val psiElement = descriptor.psiElement ?: return
    val file = psiElement.containingFile.virtualFile ?: return
    val declarationPointer = SmartPointerManager.createPointer(psiElement)

    PyPackageCoroutine.launch(project) {
      val manager = project.pythonPackageManagerUIFor(file) ?: return@launch
      val installed = manager.updatePackagesBackground(listOf(outdatedPackage)) ?: return@launch
      val installedVersion = installedVersionsByName(installed, listOf(PyPackageName.from(outdatedPackage.name)))
        .values
        .firstOrNull()
        ?: return@launch
      pinSingleDeclaration(
        project = project,
        declarationPointer = declarationPointer,
        commandName = PyBundle.message("QFIX.NAME.update.requirement", outdatedPackage.name),
        newVersion = installedVersion,
      )
    }
  }
}
