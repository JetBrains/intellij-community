// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.addPyProject

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.python.pyproject.model.spi.PyProjectCreator
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isSuccess
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CheckReturnValue

/**
 * To be used by [PyProjectActionImpl], [AddPyProjectDialog] and tests.
 * Create with [create], then set [projectName] (if [forNewProject]) and call [createProject].
 *
 * It supports two modes: [forNewProject] ([createProject] creates new subproject named [projectName] in [where])
 * and convert existing dir ([where]) into pyproject ([projectName] is ignored)
 */
internal class PyProjectPresenter
private constructor(
  private val projectCreator: PyProjectCreator,
  private val where: Directory,
  toolName: @NlsSafe String,
  private val forNewProject: Boolean,
) {
  val actionText = if (forNewProject) {
    PyProjectTomlBundle.message("new.pyproject.create.with", toolName)
  }
  else {
    PyProjectTomlBundle.message("convert.to.pyproject.with", toolName)
  }

  /**
   * Set project name (not blank!), then call [createProject]
   */
  var projectName: @NlsSafe String = ""


  /**
   * Set [projectName] to non-empty string first if [forNewProject] (otherwise this field is ignored)
   */
  @CheckReturnValue
  suspend fun createProject(): PyResult<Unit> {
    if (forNewProject) {
      check(projectName.isNotBlank()) { "Project name not set" }
    }
    return projectCreator.createProject(where, if (forNewProject) projectName else null).also {
      if (it.isSuccess) {
        withContext(Dispatchers.IO) {
          LocalFileSystem.getInstance().refreshAndFindFileByNioFile(where)?.refresh(true, true)
        }
      }
    }
  }


  companion object {
    /**
     * [forNewProject] false means "convert existing project in [where]"
     */
    @RequiresReadLock
    fun create(where: VirtualFile, sdk: Sdk, forNewProject: Boolean): PyProjectPresenter? {
      // If provided path is a file
      val where = if (!where.isDirectory) {
        where.parent
      }
      else {
        where
      }

      if ((!forNewProject) && where.findChild(PY_PROJECT_TOML) != null) {
        // Can't convert existing project that already has pyproject.toml
        return null
      }
      val manager = PyProjectManager.forSdk(sdk)
      return PyProjectPresenter(manager, where.toNioPath(), manager.ui.toolName, forNewProject)
    }
  }
}
