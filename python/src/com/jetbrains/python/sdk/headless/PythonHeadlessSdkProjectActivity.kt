// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.headless

import com.intellij.ide.environment.EnvironmentService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.findAllPythonSdks
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setup

class PythonHeadlessSdkProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) = project.serviceAsync<PythonInProgressService>().trackConfigurationActivity {
    setupPythonSdk(project)
  }

  private suspend fun setupPythonSdk(project: Project) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    if (!Registry.`is`("ide.warmup.use.predicates")) {
      return
    }
    val interpreterPath = serviceAsync<EnvironmentService>().getEnvironmentValue(PythonEnvironmentKeyProvider.Keys.sdkKey, "")
    if (interpreterPath == "") {
      thisLogger().info("No interpreter is configured for python files")
      return
    }
    val modules = ModuleManager.getInstance(project).modules
    if (modules.any { it.pythonSdk != null }) {
      return
    }
    val baseDir = project.guessProjectDir() ?: return
    val pythonSdk = when (val preliminarySdk = findAllPythonSdks(baseDir.toNioPath()).find { it.homePath == interpreterPath }) {
      is PyDetectedSdk -> preliminarySdk.setup(listOf(*ProjectJdkTable.getInstance().getAllJdks()))
      else -> preliminarySdk
    } ?: return
    try {
      writeAction {
        ProjectJdkTable.getInstance().addJdk(pythonSdk)
      }
    } catch (e : IllegalStateException) {
      // sdk is already there.
    }
    for (pythonHeadlessSdkModifier in PythonHeadlessSdkModifier.EP_NAME.extensionList) {
      if (pythonHeadlessSdkModifier.setSdk(project, pythonSdk)) {
        return
      }
    }
    for (module in ModuleManager.getInstance(project).modules) {
      module.pythonSdk = pythonSdk
    }
  }
}