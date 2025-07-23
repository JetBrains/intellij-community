// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.requirements.RequirementsFileType

internal sealed class PipPackageManagerAction : PythonPackageManagerAction<PipPythonPackageManager, String>() {
  override fun isWatchedFile(virtualFile: VirtualFile?): Boolean {
    return virtualFile?.fileType is RequirementsFileType
  }

  override fun getManager(e: AnActionEvent): PipPythonPackageManager? = e.getPythonPackageManager()
}


internal class PipSetDefaultRequirementsAction() : PipPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: PipPythonPackageManager): PyResult<Unit> {
    val envFile = e.getData(PlatformDataKeys.VIRTUAL_FILE) ?: return PyResult.success(Unit)
    val project = e.project ?: return PyResult.success(Unit)

    val newFilePath = envFile.toNioPath()
    PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(project, manager.sdk, newFilePath)
    return PyResult.success(Unit)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isEnabledAndVisible)
      return
    val manager = e.getPythonPackageManager<PipPythonPackageManager>() ?: return
    val savedFile = PythonRequirementTxtSdkUtils.findRequirementsTxt(manager.sdk)
    val currentFile = e.getData(PlatformDataKeys.VIRTUAL_FILE) ?: return

    if (savedFile == currentFile) {
      e.presentation.isEnabledAndVisible = false
    }

  }
}


internal class PipUpdateEnvAction() : PipPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: PipPythonPackageManager): PyResult<Unit> {
    val currentFile = e.getData(PlatformDataKeys.VIRTUAL_FILE) ?: return PyResult.success(Unit)
    return manager.syncRequirementsTxt(currentFile)
  }
}