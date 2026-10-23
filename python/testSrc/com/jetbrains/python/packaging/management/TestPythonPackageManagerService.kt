// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.testFramework.replaceService
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.pip.PyPiPackageCache
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.mockito.Mockito

@TestOnly
internal class TestPythonPackageManagerService(
  val installedPackages: List<PythonPackage> = emptyList(),
  val availablePackageNames: List<String> = emptyList(),
) : PythonPackageManagerService {
  private val cache: MutableMap<PythonInterpreter, PythonPackageManager> = ConcurrentHashMap()

  override fun forPythonInterpreter(project: Project, interpreter: PythonInterpreter): PythonPackageManager {
    val manager = cache.computeIfAbsent(interpreter) { createManager(project, interpreter) }
    // Production drives manager init via package UI / sync / FUS / install paths; tests
    // bypass those paths, so trigger init here so the inspection-side snapshot is ready
    // before the test starts reading it.
    //
    // The wait runs outside `computeIfAbsent`, which holds the bin lock of the map for the whole
    // mapping function, so a second thread no longer waits on the map while this one blocks.
    //
    // The init takes a read action of its own, see `TestPythonPackageManager.listDeclaredPackages`.
    // A caller that already holds the read lock therefore deadlocks here as soon as a write action
    // queues: the write action waits for that read action to end, and the init waits for the write
    // action to let a new read action start. The python LSP tools read a tool version through a
    // snapshot for exactly that reason, see `pyLspServeKeys`. `waitForInit` is idempotent, so the
    // wait costs a caller nothing after the first one.
    runBlocking { manager.waitForInit() }
    return manager
  }

  private fun createManager(project: Project, interpreter: PythonInterpreter): TestPythonPackageManager {
    val repoPackageNames = (installedPackages.map { it.name } + availablePackageNames).distinct()

    if (installedPackages.isEmpty()) {
      return TestPythonPackageManager(project, interpreter)
        .withPackageNames(repoPackageNames)
        .also { Disposer.register(project, it) }
    }

    return TestPythonPackageManager(project, interpreter)
      .withPackageInstalled(installedPackages)
      .withPackageNames(repoPackageNames)
      .withPackageDetails(PythonSimplePackageDetails(installedPackages.first().name, listOf(installedPackages.first().version),
                                                     TestPackageRepository(repoPackageNames.toSet()))).also {
        Disposer.register(project, it)
      }
  }

  override fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    return PythonPackageManagementServiceBridge(project, sdk)
  }

    companion object {


    @JvmStatic
    @JvmOverloads
    fun replacePythonPackageManagerServiceWithTestInstance(
      project: Project,
      installedPackages: List<PythonPackage> = emptyList(),
      availablePackageNames: List<String> = emptyList(),
    ) {
      project.replaceService(
        PythonPackageManagerService::class.java,
        TestPythonPackageManagerService(installedPackages, availablePackageNames),
        project,
      )
    }

    @JvmStatic
    fun replacePyPiPackageCacheService(project: Project, cache: List<String>) {
      val knownPackages = cache.toSet()
      val mock = Mockito.mock(PyPiPackageCache::class.java) { invocation ->
        when (invocation.method.name) {
          "contains",
          "tryContains" -> knownPackages.contains(invocation.arguments[0] as? String)
          else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
      }
      ApplicationManager
        .getApplication()
        .replaceService(
          PyPiPackageCache::class.java,
          mock,
          project,
        )
    }
  }
}
