// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findEvoPyProject
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

// this is HighPriorityAction because we always want our fix to appear above lsp tools because our fix is much better
@ApiStatus.Internal
open class InstallPackageQuickFix(val packageName: String) : LocalQuickFix, HighPriorityAction {
  override fun getFamilyName(): @Nls String = PyBundle.message("python.unresolved.reference.inspection.install.package", packageName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement?.containingFile?.originalFile?.virtualFile ?: return
    PyPackageCoroutine.launch(project) {
      val evoPyProject = project.findEvoPyProject(file, mainForOrphans = false) ?: return@launch
      val interpreter = evoPyProject.interpreter ?: return@launch
      PythonPackageManagerUI.forPythonInterpreter(project, interpreter)
        .installWithConfirmation(listOf(packageName), evoPyProject.pyProject.residesOnModule) ?: return@launch
      onSuccess(project, descriptor)
      PyPackagesUsageCollector.installSingleEvent.log()
    }
  }

  override fun startInWriteAction(): Boolean = false

  override fun availableInBatchMode(): Boolean = false

  open suspend fun onSuccess(project: Project, descriptor: ProblemDescriptor) {}

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}