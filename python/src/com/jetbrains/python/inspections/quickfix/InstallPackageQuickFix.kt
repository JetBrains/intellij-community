// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.requirement.RunningPackagingTasksListener
import com.jetbrains.python.packaging.PyPackageInstallUtils.confirmInstall
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import org.jetbrains.annotations.Nls

internal open class InstallPackageQuickFix(open val packageName: String) : LocalQuickFix {

  override fun getFamilyName(): @Nls String = PyBundle.message("python.unresolved.reference.inspection.install.package", packageName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!confirmInstall(project, packageName)) return

    descriptor.psiElement.let { element ->
      val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
      val sdk = PythonSdkUtil.findPythonSdk(element) ?: return

      PyInstallRequirementsFix(
        familyName, module, sdk,
        listOf(pyRequirement(packageName)),
        listener = object : RunningPackagingTasksListener(module) {
          override fun finished(exceptions: List<ExecutionException>) {
            super.finished(exceptions)
            if (exceptions.isEmpty()) {
              onSuccess(descriptor)
            }
          }
        }
      ).applyFix(module.project, descriptor)

      PyPackagesUsageCollector.installSingleEvent.log()
    }
  }

  override fun startInWriteAction(): Boolean = false

  override fun availableInBatchMode(): Boolean = false

  open fun onSuccess(descriptor: ProblemDescriptor?) { }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY

  companion object {
    const val CONFIRM_PACKAGE_INSTALLATION_PROPERTY: String = "python.confirm.package.installation"
  }
}