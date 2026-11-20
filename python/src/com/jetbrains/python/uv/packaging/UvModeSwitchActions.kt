// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.packaging

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.python.sdk.backend.writeRequirementsPath
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.requirements.RequirementsFileType
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.sdk.uv.UvMode
import com.jetbrains.python.sdk.uv.UvPackageManagerBase
import com.jetbrains.python.sdk.uv.UvPipPackageManager
import com.jetbrains.python.sdk.uv.uvMode

/** A requirements file or a `pyproject.toml`. The editor banner of such a file and the interpreter popup offer a switch. */
private fun isDependencyFile(file: VirtualFile?): Boolean =
  file != null && (file.fileType is RequirementsFileType || file.name == PY_PROJECT_TOML)

/** Switches an SDK in pip mode to project mode. Runs `uv init` first when the working directory has no `pyproject.toml`. */
internal class UvSwitchToProjectModeAction : PythonPackageManagerAction<UvPipPackageManager, String>() {
  override fun isWatchedFile(virtualFile: VirtualFile?): Boolean = isDependencyFile(virtualFile)

  override fun getManager(e: AnActionEvent): UvPipPackageManager? = e.getPythonPackageManager()

  override suspend fun execute(e: AnActionEvent, manager: UvPipPackageManager): PyResult<Unit> {
    val project = e.project ?: return PyResult.success(Unit)
    manager.initProjectIfNeeded().getOr { return it }
    manager.interpreter.writeRequirementsPath(UvMode.Project.requirementsFile)
    return PyResult.success(Unit)
  }
}

/**
 * Stores a requirements file as the dependency list of a uv SDK.
 *
 * In project mode this switches the SDK to pip mode, with the clicked requirements file or `requirements.txt`. In pip
 * mode it picks another requirements file, and it hides for the stored one.
 */
internal class UvSwitchToPipModeAction : PythonPackageManagerAction<UvPackageManagerBase, String>() {
  /** Stores a path on the SDK and leaves the environment alone. A read-only SDK keeps this action. */
  override val modifiesEnvironment: Boolean = false

  override fun isWatchedFile(virtualFile: VirtualFile?): Boolean = isDependencyFile(virtualFile)

  override fun getManager(e: AnActionEvent): UvPackageManagerBase? = e.getPythonPackageManager()

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isEnabledAndVisible) return
    val manager = getManager(e) ?: return
    val file = e.getData(PlatformDataKeys.VIRTUAL_FILE) ?: return
    when (manager.interpreter.uvMode()) {
      UvMode.Project -> e.presentation.text = message("action.UvSwitchToPipModeAction.text")
      is UvMode.Pip -> {
        val storedFile = PythonRequirementTxtSdkUtils.resolvePersistedRequirementsFile(manager.interpreter)
        e.presentation.isEnabledAndVisible = file.fileType is RequirementsFileType && file != storedFile
        e.presentation.text = message("python.uv.pip.set.default.requirements")
      }
    }
  }

  override suspend fun execute(e: AnActionEvent, manager: UvPackageManagerBase): PyResult<Unit> {
    val project = e.project ?: return PyResult.success(Unit)
    val file = e.getData(PlatformDataKeys.VIRTUAL_FILE) ?: return PyResult.success(Unit)
    val path = if (file.fileType is RequirementsFileType) file.toNioPath() else UvMode.Pip().requirementsFile
    manager.interpreter.writeRequirementsPath(path)
    return PyResult.success(Unit)
  }
}
