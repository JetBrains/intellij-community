// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkUtil

internal class InstallAllRequirementsInTomlQuickFix(val requirements: List<PyRequirement>) : LocalQuickFix {
  override fun getFamilyName(): String {
    return PyBundle.message("QFIX.NAME.install.all.requirements")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val sdk = PythonSdkUtil.findPythonSdk(descriptor.psiElement) ?: return
    PyPackageCoroutine.launch(project) {
      PythonPackageManagerUI.forSdk(project, sdk).installPyRequirementsDetachedWithConfirmation(requirements)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean = false
}