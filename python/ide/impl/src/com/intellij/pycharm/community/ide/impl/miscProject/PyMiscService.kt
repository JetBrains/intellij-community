// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.impl.MiscProjectUsageCollector
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.util.ShowingMessageErrorSync
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
      val projectCreationResult = com.intellij.pycharm.community.ide.impl.miscProject.impl.createMiscProject(
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
            ShowingMessageErrorSync.emit(projectCreationResult.error, project)
          }
        }
      }
    }
  }

  companion object {
    fun getInstance(): PyMiscService = service()
  }
}
