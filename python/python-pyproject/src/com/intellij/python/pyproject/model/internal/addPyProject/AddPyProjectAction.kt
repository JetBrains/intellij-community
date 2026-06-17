// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.addPyProject

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFileSystemItem
import com.intellij.python.pyproject.icons.PythonPyprojectIcons
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyErrorDetail
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action to show [AddPyProjectDialog]
 */
internal class AddPyProjectAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val model = e.projectCreationModel ?: return
    val project = e.project ?: return
    val dialog = AddPyProjectDialog(project, model)
    if (dialog.showAndGet()) {
      project.service<MyService>().scope.launch {
        when (val r = model.createProject()) {
          is Result.Failure -> ErrorSink().emit(PyErrorDetail(r.error))
          is Result.Success -> Unit
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val projectCreationModel = e.projectCreationModel
    e.presentation.isEnabledAndVisible = e.project != null && projectCreationModel != null
    if (projectCreationModel != null) {
      e.presentation.text = projectCreationModel.actionText
      e.presentation.icon = PythonPyprojectIcons.Model.PyProjectModule
    }
  }
}

@get:VisibleForTesting
internal val AnActionEvent.projectCreationModel: AddPyProjectPresenter?
  get() {
    if (project == null) {
      return null // Without project, no need to bother with model
    }
    var vPath = ((PlatformCoreDataKeys.PSI_ELEMENT.getData(dataContext) as? PsiFileSystemItem)?.virtualFile
                 ?: CommonDataKeys.VIRTUAL_FILE.getData(dataContext))
                ?: return null
    if (!vPath.isDirectory) {
      vPath = vPath.parent
    }
    val path = vPath.toNioPath()

    @Suppress("UsagesOfObsoleteApi") // Actions aren't suspendable
    val sdk = LangDataKeys.MODULE.getData(dataContext)?.pythonSdk ?: return null
    return AddPyProjectPresenter.create(path, sdk)
  }

@Service(Service.Level.PROJECT)
private class MyService(val scope: CoroutineScope)
