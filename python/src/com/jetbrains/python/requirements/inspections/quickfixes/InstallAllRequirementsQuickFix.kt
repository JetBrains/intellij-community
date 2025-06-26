// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageInstallUtils
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.getPythonSdk

internal class InstallAllRequirementsQuickFix(val requirements: List<PyRequirement>) : LocalQuickFix {
  override fun getFamilyName(): String {
    return PyBundle.message("QFIX.NAME.install.all.requirements")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val confirmedPackages = PyPackageInstallUtils.getConfirmedPackages(requirements, project)
    if (confirmedPackages.isEmpty())
      return

    val file = descriptor.psiElement.containingFile ?: return

    PyPackageCoroutine.launch(project) {
      val sdk = getPythonSdk(file) ?: return@launch
      val manager = PythonPackageManagerUI.forSdk(project, sdk)
      manager.installPyRequirementsBackground(confirmedPackages.toList(), emptyList<String>()
      )
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean = false
}