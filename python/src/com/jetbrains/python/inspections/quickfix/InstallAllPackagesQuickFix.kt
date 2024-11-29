// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.PyInstallRequirementsFix
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.RunningPackagingTasksListener
import com.jetbrains.python.packaging.getConfirmedPackages
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.statistics.PyPackagesUsageCollector

class InstallAllPackagesQuickFix : LocalQuickFix {
  var packageNames: List<String> = emptyList()

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    val sdk = PythonSdkUtil.findPythonSdk(element) ?: return

    val confirmedPackages = getConfirmedPackages(packageNames, project)
    if (confirmedPackages.isEmpty()) {
      return
    }

    val requirements = confirmedPackages.map { pyRequirement(it) }

    val fix = PyInstallRequirementsFix(familyName, module, sdk,
                                       requirements,
                                       emptyList(),
                                       RunningPackagingTasksListener(module))
    fix.applyFix(module.project, descriptor)
    PyPackagesUsageCollector.installAllEvent.log(requirements.size)
  }

  override fun getFamilyName() = PyBundle.message("python.unresolved.reference.inspection.install.all")

  override fun startInWriteAction(): Boolean = false

  override fun availableInBatchMode() = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}