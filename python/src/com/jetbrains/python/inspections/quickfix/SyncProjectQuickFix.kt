// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findPythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SyncProjectQuickFix : LocalQuickFix {
  override fun getFamilyName(): String = PyBundle.message("python.sdk.intention.family.name.sync.project")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val file = element.containingFile?.originalFile?.virtualFile ?: return
    PyPackageCoroutine.launch(project) {
      val interpreter = project.findPythonInterpreter(file, mainForOrphans = false) ?: return@launch
      val packageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)
      val managerUI = PythonPackageManagerUI.forPythonInterpreter(project, interpreter)
      managerUI.executeCommand(PyBundle.message("python.sdk.sync.project.text")) {
        withContext(Dispatchers.Default) {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        packageManager.syncLocked()
      }
      DaemonCodeAnalyzer.getInstance(project).restart(element.containingFile, this)
    }
  }
}