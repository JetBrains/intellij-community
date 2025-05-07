// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.Strings
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.jetbrains.python.sdk.PySdkFromEnvironmentVariable
import org.jetbrains.annotations.ApiStatus

private val LOGGER = logger<PySdkFromEnvironmentVariableConfigurator>()

@ApiStatus.Internal
internal class PySdkFromEnvironmentVariableConfigurator(private val project: Project) : JpsProjectLoadedListener {
  override fun loaded() {
    val pycharmPythonPathEnvVariable = PySdkFromEnvironmentVariable.getPycharmPythonPathProperty()
    if (Strings.isEmptyOrSpaces(pycharmPythonPathEnvVariable)) {
      LOGGER.debug("$PySdkFromEnvironmentVariable.PYCHARM_PYTHON_PATH is null or empty")
      return
    }
    LOGGER.info("Found $PySdkFromEnvironmentVariable.PYCHARM_PYTHON_PATH='${PySdkFromEnvironmentVariable.getPycharmPythonPathProperty()}' system property")

    checkAndSetSdk(project, pycharmPythonPathEnvVariable!!)
  }

  private fun checkAndSetSdk(project: Project, pycharmPythonPathEnvVariable: String) {
    runInEdt {
      val sdk = PySdkFromEnvironmentVariable.findOrCreateSdkByPath(pycharmPythonPathEnvVariable) ?: return@runInEdt

      val projectSdk = ProjectRootManager.getInstance(project).projectSdk

      ModuleManager.getInstance(project).modules.forEach {
        PySdkFromEnvironmentVariable.setModuleSdk(it, projectSdk, sdk, pycharmPythonPathEnvVariable)
      }
    }
  }
}