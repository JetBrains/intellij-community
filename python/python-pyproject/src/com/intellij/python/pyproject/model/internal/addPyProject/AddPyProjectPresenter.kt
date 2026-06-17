// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.addPyProject

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.python.pyproject.model.spi.PyProjectCreator
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isSuccess
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CheckReturnValue

/**
 * To be used by [AddPyProjectDialog] and tests.
 * Create with [create], then set [projectName] and call [createProject]
 */
internal class AddPyProjectPresenter
private constructor(
  private val projectCreator: PyProjectCreator,
  private val where: Directory,
  toolName: @NlsSafe String,
) {
  val actionText = PyProjectTomlBundle.message("new.pyproject.create.with", toolName)

  /**
   * Set project name (not blank!), then call [createProject]
   */
  var projectName: @NlsSafe String = ""


  /**
   * Set [projectName] to non-empty string first
   */
  @CheckReturnValue
  suspend fun createProject(): PyResult<Unit> {
    check(projectName.isNotBlank()) { "Project name not set" }
    return projectCreator.createProject(where, projectName).also {
      if (it.isSuccess) {
        withContext(Dispatchers.IO) {
          LocalFileSystem.getInstance().refreshAndFindFileByNioFile(where)?.refresh(true, true)
        }
      }
    }
  }


  companion object {
    fun create(where: Directory, sdk: Sdk): AddPyProjectPresenter {
      val manager = PyProjectManager.forSdk(sdk)
      return AddPyProjectPresenter(manager, where, manager.ui.toolName)
    }
  }
}
