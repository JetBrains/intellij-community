// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.util.IntellijInternalApi
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil

internal class PyDependencyCollector : DependencyCollector {
  override suspend fun collectDependencies(project: Project): Collection<DependencyInformation> {
    val modules = readAction {
      ModuleManager.getInstance(project).modules
    }
    return modules.flatMap { module ->
      val pythonSdk = readAction { PythonSdkUtil.findPythonSdk(module) } ?: return@flatMap emptySequence()

      val pyPackageManager = PythonPackageManager.forSdk(project, pythonSdk)
      pyPackageManager.listInstalledPackages().asSequence().map { DependencyInformation(it.name) }
    }.toSet()
  }
}

@OptIn(IntellijInternalApi::class)
private class PyDependencyCollectorListener(private val project: Project) : PythonPackageManagementListener {
  override fun packagesChanged(sdk: Sdk) {
    PluginAdvertiserService.getInstance(project).rescanDependencies()
  }
}