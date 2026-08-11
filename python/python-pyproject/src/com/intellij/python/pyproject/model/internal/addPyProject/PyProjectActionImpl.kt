// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.addPyProject

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFileSystemItem
import com.intellij.python.pyproject.icons.PythonPyprojectIcons
import com.intellij.python.pyproject.model.internal.PyProjectScopeService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyErrorDetail
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action for both create new pyproject and convert existing. It is called when user clicks on the directory.
 * in [forNewProject] in shows [AddPyProjectDialog] to ask user for a project name, and creates subproject.
 * Otherwise, it just creates project directly in the directory used clicked on.
 */
internal abstract class PyProjectActionImpl protected constructor(private val forNewProject: Boolean) : AnAction() {
  init {
    templatePresentation.isRWLockRequired = true
    templatePresentation.icon = PythonPyprojectIcons.Model.PyProjectModule
  }

  @RequiresReadLock
  @RequiresEdt
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val presenter = e.projectCreationPresenter(forNewProject) ?: return
    if (!forNewProject || AddPyProjectDialog(project, presenter).showAndGet()) {
      project.service<PyProjectScopeService>().scope.launch {
        when (val r = presenter.createProject()) {
          is Result.Failure -> ErrorSink().emit(PyErrorDetail(r.error))
          is Result.Success -> Unit
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  @RequiresBackgroundThread
  @RequiresReadLock
  override fun update(e: AnActionEvent) {
    val projectCreationModel = e.projectCreationPresenter(forNewProject)
    e.presentation.isEnabledAndVisible = projectCreationModel != null
    if (projectCreationModel != null) {
      e.presentation.text = projectCreationModel.actionText
    }
  }
}

@RequiresReadLock
@VisibleForTesting
internal fun AnActionEvent.projectCreationPresenter(forNewProject: Boolean): PyProjectPresenter? {
  if (project == null) {
    return null // Without project, no need to bother with model
  }
  val vPath = ((PlatformCoreDataKeys.PSI_ELEMENT.getData(dataContext) as? PsiFileSystemItem)?.virtualFile
               ?: CommonDataKeys.VIRTUAL_FILE.getData(dataContext))
              ?: return null

  @Suppress("UsagesOfObsoleteApi") // action doesn't support suspend API
  val sdk = LangDataKeys.MODULE.getData(dataContext)?.pythonSdk ?: return null
  return PyProjectPresenter.create(where = vPath, sdk = sdk, forNewProject = forNewProject)
}

