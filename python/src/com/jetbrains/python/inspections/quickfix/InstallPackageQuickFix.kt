// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.externalIndex.PyExternalFilesIndexService
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import org.jetbrains.annotations.Nls

// this is HighPriorityAction because we always want our fix to appear above lsp tools because our fix is much better
internal open class InstallPackageQuickFix(open val packageName: String) : LocalQuickFix, HighPriorityAction {
  override fun getFamilyName(): @Nls String = PyBundle.message("python.unresolved.reference.inspection.install.package", packageName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val sdk = PythonSdkUtil.findPythonSdk(descriptor.psiElement)
              ?: project.service<PyExternalFilesIndexService>().findSdkForExternallyIndexedFile(descriptor.psiElement.containingFile.virtualFile)
              ?: return
    PyPackageCoroutine.launch(project) {
      PythonPackageManagerUI.forSdk(project, sdk).installWithConfirmation(listOf(packageName)) ?: return@launch
      onSuccess(descriptor)
      PyPackagesUsageCollector.installSingleEvent.log()
    }
  }

  override fun startInWriteAction(): Boolean = false

  override fun availableInBatchMode(): Boolean = false

  open fun onSuccess(descriptor: ProblemDescriptor?) {}

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}