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
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.internal.PyProjectScopeService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyErrorDetail
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action for both create new pyproject and convert existing. It is called when user clicks on the directory.
 * in [forNewProject] in shows [AddPyProjectDialog] to ask user for a project name, and creates subproject.
 * Otherwise, it just creates project directly in the directory used clicked on.
 *
 * The action is hidden until the first [com.intellij.python.pyproject.model.evolution.EvoPyProjectModel] snapshot is
 * ready, because the tool it offers comes from the interpreter the snapshot holds.
 */
internal abstract class PyProjectActionImpl protected constructor(private val forNewProject: Boolean) : AnAction() {
  init {
    templatePresentation.isRWLockRequired = true
    templatePresentation.icon = PythonPyprojectIcons.Model.PyProjectModule
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
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

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  override fun update(e: AnActionEvent) {
    val projectCreationModel = e.projectCreationPresenter(forNewProject)
    e.presentation.isEnabledAndVisible = projectCreationModel != null
    if (projectCreationModel != null) {
      e.presentation.text = projectCreationModel.actionText
    }
  }
}

@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
@VisibleForTesting
internal fun AnActionEvent.projectCreationPresenter(forNewProject: Boolean): PyProjectPresenter? {
  if (project == null) {
    return null // Without project, no need to bother with model
  }
  val vPath = ((PlatformCoreDataKeys.PSI_ELEMENT.getData(dataContext) as? PsiFileSystemItem)?.virtualFile
               ?: CommonDataKeys.VIRTUAL_FILE.getData(dataContext))
              ?: return null

  val module = LangDataKeys.MODULE.getData(dataContext) ?: return null
  val snapshot = EvoPyProjectModel.getInstance(module.project).snapshotOrNull() ?: return null
  val interpreter = snapshot.evoPyProjects.firstOrNull { it.pyProject.residesOnModule == module }?.interpreter ?: return null
  return PyProjectPresenter.create(where = vPath, interpreter = interpreter, forNewProject = forNewProject)
}

