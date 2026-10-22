// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.management.ui.updatePackagesBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal class UpdateAllRequirementQuickFix(val outdatedPackages: List<PythonOutdatedPackage>) : LocalQuickFix {
  override fun getFamilyName() = PyBundle.message("QFIX.NAME.update.all.requirements")
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile?.virtualFile ?: return

    PyPackageCoroutine.launch(project) {
      val manager = project.pythonPackageManagerUIFor(file) ?: return@launch
      val actualOutdatedPackages = manager.manager.listOutdatedPackages()
      val packagesForUpdate = outdatedPackages
        .filter { it.name in actualOutdatedPackages }
        .ifEmpty { return@launch }

      val installed = manager.updatePackagesBackground(packagesForUpdate) ?: return@launch
      val installedByName = installedVersionsByName(installed, packagesForUpdate.map { PyPackageName.from(it.name) })
      if (installedByName.isEmpty()) return@launch
      pinDeclarationsForFile(
        project = project,
        file = file,
        commandName = PyBundle.message("QFIX.NAME.update.all.requirements"),
        installedByName = installedByName,
      )
    }
  }
}
