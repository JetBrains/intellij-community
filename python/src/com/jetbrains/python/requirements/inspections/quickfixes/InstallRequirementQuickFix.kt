// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageInstallUtils
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.getPythonSdk

internal class InstallRequirementQuickFix(val requirement: PyRequirement) : LocalQuickFix {
  override fun getFamilyName(): String {
    return PyBundle.message("QFIX.NAME.install.requirement", requirement.presentableText)
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val pythonSdk = getPythonSdk(descriptor.psiElement.containingFile) ?: return

    PyPackageCoroutine.launch(project) {
      PyPackageInstallUtils.confirmAndInstall(project, pythonSdk, requirement)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY

  override fun startInWriteAction(): Boolean = false
}