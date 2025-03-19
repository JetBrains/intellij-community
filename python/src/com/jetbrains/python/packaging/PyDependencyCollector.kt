// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil

internal class PyDependencyCollector : DependencyCollector {
  override suspend fun collectDependencies(project: Project): Collection<String> {
    return readAction {
      ModuleManager.getInstance(project).modules.asSequence()
        .flatMap { module ->
          val pythonSdk = PythonSdkUtil.findPythonSdk(module) ?: return@flatMap emptySequence()

          val pyPackageManager = PythonPackageManager.forSdk(project, pythonSdk)
          pyPackageManager.installedPackages.asSequence()
            .map { it.name }
        }
        .toSet()
    }
  }
}

private class PyDependencyCollectorListener(private val project: Project) : PythonPackageManagementListener {
  override fun packagesChanged(sdk: Sdk) {
    PluginAdvertiserService.getInstance(project).rescanDependencies()
  }
}