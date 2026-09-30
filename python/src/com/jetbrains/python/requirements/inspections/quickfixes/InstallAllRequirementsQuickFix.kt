// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findEvoPyProject
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal class InstallAllRequirementsQuickFix(val requirements: List<PyRequirement>) : LocalQuickFix, PriorityAction {
  override fun getFamilyName(): String {
    return PyBundle.message("QFIX.NAME.install.all.requirements")
  }

  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile.virtualFile ?: return

    PyPackageCoroutine.launch(project) {
      val evoPyProject = project.findEvoPyProject(file, mainForOrphans = false) ?: return@launch
      val interpreter = evoPyProject.interpreter ?: return@launch
      PythonPackageManagerUI.forPythonInterpreter(project, interpreter)
        .installPyRequirementsWithConfirmation(requirements, evoPyProject.pyProject.residesOnModule)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean = false
}