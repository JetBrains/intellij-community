// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonPackageManagerService(val installedPackages: List<PythonPackage> = emptyList()) : PythonPackageManagerService {

  override fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    installedPackages.ifEmpty {
      return TestPythonPackageManager(project, sdk).also { Disposer.register(project, it) }
    }

    return TestPythonPackageManager(project, sdk)
      .withPackageInstalled(installedPackages)
      .withPackageNames(installedPackages.map { it.name })
      .withPackageDetails(PythonSimplePackageDetails(installedPackages.first().name, listOf(installedPackages.first().version),
                                                     TestPackageRepository(installedPackages.map { it.name }.toSet()))).also {
        Disposer.register(project, it)
      }
  }

  override fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    return PythonPackageManagementServiceBridge(project, sdk)
  }

  override fun getServiceScope(): CoroutineScope {
    return CoroutineScope(Job())
  }

  companion object {


    @JvmStatic
    fun replacePythonPackageManagerServiceWithTestInstance(project: Project, installedPackages: List<PythonPackage> = emptyList()) {
      project.replaceService(PythonPackageManagerService::class.java, TestPythonPackageManagerService(installedPackages), project)
    }
  }
}