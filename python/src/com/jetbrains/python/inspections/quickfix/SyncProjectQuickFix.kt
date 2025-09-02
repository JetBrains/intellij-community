// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.pythonSdk

internal class SyncProjectQuickFix : LocalQuickFix {
  override fun getFamilyName(): String = PyBundle.message("python.sdk.intention.family.name.sync.project")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return

    val sdk = module.pythonSdk ?: return

    val packageManager = PythonPackageManager.Companion.forSdk(project, sdk)

    val managerUI = PythonPackageManagerUI.Companion.forSdk(project, sdk)
    PyPackageCoroutine.Companion.launch(project) {
      managerUI.executeCommand(PyBundle.message("python.sdk.sync.project.text")) {
        writeAction {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        packageManager.sync()
      }
      DaemonCodeAnalyzer.getInstance(project).restart(element.containingFile)
    }
  }
}