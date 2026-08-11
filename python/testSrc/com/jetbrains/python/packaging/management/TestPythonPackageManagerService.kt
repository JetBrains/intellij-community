// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.pip.PyPiPackageCache
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito
import java.util.concurrent.ConcurrentHashMap

@TestOnly
internal class TestPythonPackageManagerService(val installedPackages: List<PythonPackage> = emptyList()) : PythonPackageManagerService {
  private val cache: MutableMap<Sdk, PythonPackageManager> = ConcurrentHashMap()

  override fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    return cache.computeIfAbsent(sdk) {
      val manager = createManager(project, sdk)
      // Production drives manager init via package UI / sync / FUS / install paths; tests
      // bypass those paths, so trigger init here so the inspection-side snapshot is ready
      // before the test starts reading it.
      runBlocking { manager.waitForInit() }
      manager
    }
  }

  private fun createManager(project: Project, sdk: Sdk): TestPythonPackageManager {
    if (installedPackages.isEmpty()) {
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

    companion object {


    @JvmStatic
    fun replacePythonPackageManagerServiceWithTestInstance(project: Project, installedPackages: List<PythonPackage> = emptyList()) {
      project.replaceService(PythonPackageManagerService::class.java, TestPythonPackageManagerService(installedPackages), project)
    }

    @JvmStatic
    fun replacePyPiPackageCacheService(project: Project, cache: List<String>) {
      val mock = Mockito.mock(PyPiPackageCache::class.java)
      Mockito.`when`(mock.contains(argThat { o -> cache.contains(o) } ?: "")).thenReturn(true)

      ApplicationManager
        .getApplication()
        .replaceService(
          PyPiPackageCache::class.java,
          mock,
          project
        )
    }
  }
}
