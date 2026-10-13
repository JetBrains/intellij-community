// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.impl.MiscProjectUsageCollector
import com.intellij.pycharm.community.ide.impl.miscProject.impl.createMiscProject as createMiscProjectAndSdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.mapResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@ApiStatus.Internal
class PyMiscService(private val scope: CoroutineScope) {
  fun createMiscProject(project: Project?, miscFileType: MiscFileType): Unit {
    scope.launch {
      val projectCreationResult = createMiscProjectAndSdk(
        miscFileType,
        confirmInstallation = {
          withContext(Dispatchers.EDT) {
            MessageDialogBuilder.yesNo(
              PyCharmCommunityCustomizationBundle.message("misc.no.python.found"),
              PyCharmCommunityCustomizationBundle.message("misc.install.python.question")
            ).ask(project)
          }
        },
        scopeProvider = { scope },
        currentProject = project,
      )

      when (projectCreationResult) {
        is Result.Success -> {
          MiscProjectUsageCollector.projectCreated(miscFileType)
        }
        is Result.Failure -> {
          withContext(Dispatchers.EDT) {
            ErrorSink().emit(projectCreationResult.error, project)
          }
        }
      }
    }
  }

  /**
   * Configures the Python interpreter of [project], then calls [configureInterpreter], if given, with the result.
   * The service reports a failure of either step to the user.
   */
  fun createMiscProject(
    project: Project,
    configureInterpreter: (suspend (Project, PythonInterpreter) -> PyResult<Unit>)? = null,
  ) {
    scope.launch {
      val result = createMiscProjectAndSdk(
        confirmInstallation = {
          withContext(Dispatchers.EDT) {
            MessageDialogBuilder.yesNo(
              PyCharmCommunityCustomizationBundle.message("misc.no.python.found"),
              PyCharmCommunityCustomizationBundle.message("misc.install.python.question")
            ).ask(project)
          }
        },
        currentProject = project,
      ).mapResult { (miscProject, pythonInterpreter) ->
        if (configureInterpreter == null) {
          PyResult.success(Unit)
        }
        else {
          withBackgroundProgress(miscProject, PyCharmCommunityCustomizationBundle.message("misc.project.filling.file")) {
            configureInterpreter(miscProject, pythonInterpreter)
          }
        }
      }

      when (result) {
        is Result.Success -> Unit
        is Result.Failure -> {
          withContext(Dispatchers.EDT) {
            ErrorSink().emit(result.error, project)
          }
        }
      }
    }
  }

  companion object {
    fun getInstance(): PyMiscService = service()
  }
}
