// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.python.pyproject.model.evolution.pythonInterpreters
import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager

internal class PyDependencyCollector : DependencyCollector {
  override suspend fun collectDependencies(project: Project): Collection<DependencyInformation> {
    return project.pythonInterpreters().flatMap { interpreter ->
      val pyPackageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)
      pyPackageManager.listInstalledPackages().asSequence().map { DependencyInformation(it.name) }
    }.toSet()
  }
}

private class PyDependencyCollectorListener(private val project: Project) : PythonPackageManagementListener {
  override fun packagesChanged(sdk: Sdk) {
    PluginAdvertiserService.getInstance(project).rescanDependencies()
  }
}