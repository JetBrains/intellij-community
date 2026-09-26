// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.jetbrains.python.sdk.PySdkFromEnvironmentVariable
import com.jetbrains.python.sdk.getOrLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


/**
 * Configures Python SDK from env variable, see [PySdkFromEnvironmentVariable]
 */
internal class PySdkFromEnvironmentVariableConfigurator(private val project: Project) : JpsProjectLoadedListener {
  private companion object {
    val log = fileLogger()
  }

  override fun loaded() {
    project.service<MyService>().scope.launch {
      GlobalWorkspaceModel.getInstance(project.getEelMachine()).awaitSynchronizationWithJpsModel()
      @Suppress("UnsafeOpenServiceCast")
      (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()
      PySdkFromEnvironmentVariable.create(project).getOrLog(log)?.configureSdkForModulesLogIfError(log)
    }
  }
}

@Service(Service.Level.PROJECT)
private class MyService(val scope: CoroutineScope)