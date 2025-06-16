// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.updatePackagesByNamesBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.getPythonSdk
import kotlinx.coroutines.launch

internal class UpdateRequirementQuickFix(private val packageName: String) : LocalQuickFix {
  override fun getFamilyName() = PyBundle.message("QFIX.NAME.update.requirement", packageName)
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile ?: return
    val sdk = getPythonSdk(file) ?: return
    val manager = PythonPackageManagerUI.forSdk(project, sdk)

    PyPackageCoroutine.getScope(project).launch {
      manager.updatePackagesByNamesBackground(listOf(packageName))
    }
  }
}