// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findPythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.updatePackagesBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal class UpdateRequirementQuickFix(private val outdatedPackage: PythonOutdatedPackage) : LocalQuickFix, PriorityAction {
  override fun getFamilyName() = PyBundle.message("QFIX.NAME.update.requirement", outdatedPackage.name)
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile.virtualFile ?: return

    PyPackageCoroutine.launch(project) {
      val interpreter = project.findPythonInterpreter(file, mainForOrphans = false) ?: return@launch
      val manager = PythonPackageManagerUI.forPythonInterpreter(project, interpreter)
      manager.updatePackagesBackground(listOf(outdatedPackage))
    }
  }
}