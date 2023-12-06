// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyproject

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDocument
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.launch

class PyInstallProjectAsEditableQuickfix : LocalQuickFix {

  override fun getFamilyName(): String {
    return PyBundle.message("python.pyproject.install.self.as.editable")
  }

  @Suppress("DialogTitleCapitalization")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val file = descriptor.psiElement.containingFile ?: return
    val sdk = ModuleUtilCore.findModuleForPsiElement(element)?.pythonSdk ?: return
    val manager = PythonPackageManager.forSdk(project, sdk)
    FileDocumentManager.getInstance().saveDocument(file.virtualFile.findDocument() ?: return)

    project.service<PyPackagingToolWindowService>().serviceScope.launch {
      runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.pyproject.install.self.error"), null) {
        manager.runPackagingTool("install", listOf("-e", "."), PyBundle.message("python.pyproject.install.self.as.editable.progress"))
        manager.refreshPaths()
        manager.reloadPackages()
      }
      DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }
}