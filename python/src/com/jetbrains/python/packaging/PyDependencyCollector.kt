// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.jetbrains.python.sdk.PythonSdkUtil

internal class PyDependencyCollector : DependencyCollector {
  override fun collectDependencies(project: Project): Collection<String> {
    return ModuleManager.getInstance(project).modules.asSequence()
      .flatMap { module ->
        ProgressManager.checkCanceled()

        runReadAction {
          val pythonSdk = PythonSdkUtil.findPythonSdk(module)
          if (pythonSdk == null) return@runReadAction emptyList()

          val pyPackageManager = PyPackageManager.getInstance(pythonSdk)
          pyPackageManager.packages?.map { it.name } ?: emptyList()
        }
      }
      .toList()
  }
}

private class PyDependencyCollectorListener(private val project: Project) : PyPackageManager.Listener {
  override fun packagesRefreshed(sdk: Sdk) {
    PluginAdvertiserService.getInstance(project).rescanDependencies()
  }
}