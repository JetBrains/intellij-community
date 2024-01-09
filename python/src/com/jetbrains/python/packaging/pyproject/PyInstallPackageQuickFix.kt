// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyproject

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.createSpecification
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PyInstallPackageQuickFix(val packageName: String) : LocalQuickFix {


  override fun getFamilyName(): String {
    return PyBundle.message("python.pyproject.install.package", packageName)
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val file = descriptor.psiElement.containingFile ?: return
    val sdk = ModuleUtilCore.findModuleForPsiElement(element)?.pythonSdk ?: return
    val manager = PythonPackageManager.forSdk(project, sdk)
    val requirement = PyRequirementParser.fromLine(element.text.removeSurrounding("\"")) ?: return

    project.service<PyPackagingToolWindowService>().serviceScope.launch(Dispatchers.IO) {
      val versionSpec = requirement.versionSpecs.firstOrNull()
      val specification = manager.repositoryManager.createSpecification(requirement.name, versionSpec?.version, versionSpec?.relation) ?: return@launch
      manager.installPackage(specification)
      DaemonCodeAnalyzer.getInstance(project).restart(file)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}