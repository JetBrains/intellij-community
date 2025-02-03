// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.getConfirmedPackages
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import org.jetbrains.annotations.Nls

class InstallAllPackagesQuickFix(private val packageNames: List<String>) : LocalQuickFix {

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    val sdk = PythonSdkUtil.findPythonSdk(element) ?: return
    val confirmedPackages = getConfirmedPackages(packageNames.map { pyRequirement(it) }, project)

    if (confirmedPackages.isEmpty()) return

    val fix = PyInstallRequirementsFix(familyName, module, sdk,
                                       confirmedPackages.toList(),
                                       emptyList())
    fix.applyFix(module.project, descriptor)
    PyPackagesUsageCollector.installAllEvent.log(confirmedPackages.size)
  }

  override fun getFamilyName(): @Nls String = PyBundle.message("python.unresolved.reference.inspection.install.all")

  override fun startInWriteAction(): Boolean = false

  override fun availableInBatchMode(): Boolean = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}