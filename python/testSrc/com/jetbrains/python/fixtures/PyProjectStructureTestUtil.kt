// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.PyInterpreterLibrarySync
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageStubs
import com.jetbrains.python.packaging.management.TestPythonPackageManager
import com.jetbrains.python.packaging.management.awaitDerivedRootsForTests
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.awaitPythonLanguageLevelsForTests
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.asPromise
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

/**
 * Blocking [EvoPyProjectModel.awaitCurrentInterpreters], then [awaitDerivedRoots], then the push of the language levels
 * and the indexes: the test starts as a project does after its start. Pumps the EDT, so a test on the EDT can call it.
 */
@TestOnly
fun awaitPythonInterpreters(project: Project) {
  val job = PyPackageCoroutine.getScope(project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) {
      EvoPyProjectModel.getInstance(project).awaitCurrentInterpreters()
      awaitDerivedRootsForTests(project)
      // A push of a new layout starts a dumb mode, so a test that starts before it can run in dumb mode.
      awaitPythonLanguageLevelsForTests(project)
    }
  }
  job.asPromise().waitForPromiseAndPumpEdt(DEFAULT_TEST_TIMEOUT)
  IndexingTestUtil.waitUntilIndexesAreReady(project)
}

/**
 * Blocking [awaitDerivedRootsForTests]: the derived roots of each interpreter follow its installed packages, such as
 * after an install of a test. Pumps the EDT, so a test on the EDT can call it.
 */
@TestOnly
fun awaitDerivedRoots(project: Project) {
  val job = PyPackageCoroutine.getScope(project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) { awaitDerivedRootsForTests(project) }
  }
  job.asPromise().waitForPromiseAndPumpEdt(DEFAULT_TEST_TIMEOUT)
}

/**
 * Loads the declared packages of the interpreter of [module] again, and waits for the load. A dependency file that a test
 * adds after the first load is tracked by no load, so no file change loads it. The inspections read the snapshot of the
 * last load. Pumps the EDT.
 */
@TestOnly
fun loadDeclaredPackagesForTests(module: Module) {
  val interpreter = checkNotNull(findPythonInterpreterForTests(module)) { "No interpreter for ${module.name}" }
  val job = PyPackageCoroutine.getScope(module.project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) {
      PythonPackageManager.forPythonInterpreter(module.project, interpreter).listDeclaredPackagesCached()
    }
  }
  job.asPromise().waitForPromiseAndPumpEdt(DEFAULT_TEST_TIMEOUT)
}

/**
 * The interpreter of the Python project on [module], read from the snapshot after [awaitPythonInterpreters]. For a test
 * that reads what the fixture set up, such as its class roots or its stdlib directory.
 */
@TestOnly
fun findPythonInterpreterForTests(module: Module): PythonInterpreter? {
  awaitPythonInterpreters(module.project)
  return runReadActionBlocking { module.findEvoPyProjectIfReady()?.interpreter }
}

/**
 * The language level of the interpreter of [module], see [findPythonInterpreterForTests]. It reads the version that the
 * SDK of the interpreter records, so it also works for a mock interpreter, which has no binary to run.
 */
@TestOnly
fun findLanguageLevelForTests(module: Module): LanguageLevel {
  val interpreter = checkNotNull(findPythonInterpreterForTests(module)) { "No interpreter for ${module.name}" }
  @Suppress("DEPRECATION")
  val versionString = checkNotNull(interpreter.getSdkAPI().versionString) { "No version for $interpreter" }
  return LanguageLevel.fromPythonVersion(versionString.removePrefix("Python "))!!
}

/**
 * Sets [interpreter] as the interpreter of the Python project on [module], or removes it for `null`, and waits for the
 * snapshot. Pumps the EDT. The interpreter library entry is what names it, so a test that clears the module SDK through
 * the platform does not remove the interpreter.
 */
@TestOnly
fun setPythonInterpreterForTests(module: Module, interpreter: PythonInterpreter?) {
  val job = PyPackageCoroutine.getScope(module.project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) {
      val pyProject = checkNotNull(module.asPyProject()) { "${module.name} is not a Python project" }
      pyProject.setPythonInterpreter(interpreter).orThrow()
    }
  }
  job.asPromise().waitForPromiseAndPumpEdt(DEFAULT_TEST_TIMEOUT)
}

/**
 * Makes an interpreter of the mock [sdk] the interpreter of the Python project on [module], and returns it. The registry
 * holds it, owned by that Python project, and the SDK is in no SDK table. Pumps the EDT, so a light fixture can call it
 * while it sets up its project.
 */
@TestOnly
fun addMockPythonInterpreterForTests(module: Module, sdk: Sdk): PythonInterpreter {
  var result: PythonInterpreter? = null
  val job = PyPackageCoroutine.getScope(module.project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) {
      val pyProject = checkNotNull(module.asPyProject()) { "${module.name} is not a Python project" }
      val interpreter = PythonInterpreterRegistry.getInstance(module.project).addMockPythonInterpreter(pyProject, sdk)
      result = pyProject.setPythonInterpreter(interpreter).orThrow()
    }
  }
  job.asPromise().waitForPromiseAndPumpEdt(DEFAULT_TEST_TIMEOUT)
  return checkNotNull(result) { "No interpreter for ${module.name}" }
}

/**
 * Adds the mock [sdk] to the SDK table until the project of [module] closes, and sets it on [module]. The content root of
 * [module] owns the SDK and is its working directory, as in a real project of the legacy flow. Call it on the EDT.
 */
@TestOnly
fun addOwnedMockSdkForTests(module: Module, sdk: Sdk) {
  val ownerDir = checkNotNull(ModuleRootManager.getInstance(module).contentRoots.firstOrNull()) { "No content root of ${module.name}" }.path
  WriteAction.run<RuntimeException> {
    val modificator = sdk.sdkModificator
    (modificator.sdkAdditionalData as? PythonSdkAdditionalData)?.let {
      it.associatedModulePath = ownerDir
      it.workingDirectory = Path.of(ownerDir)
    }
    modificator.commitChanges()
    ProjectJdkTable.getInstance().addJdk(sdk, module.project)
  }
  ModuleRootModificationUtil.setModuleSdk(module, sdk)
}

/**
 * Blocking [PyInterpreterLibrarySync.syncForTests] for every open project. Pumps the EDT. For a test that changes the
 * roots of an interpreter: the interpreter library of each module then holds them.
 */
@TestOnly
fun syncPythonInterpreterLibraries() {
  ProjectManager.getInstance().openProjects.forEach(::syncPythonInterpreterLibraries)
}

/** [syncPythonInterpreterLibraries] for [project] only, also a project that is not open, such as one of a project rule. */
@TestOnly
fun syncPythonInterpreterLibraries(project: Project) {
  val job = PyPackageCoroutine.getScope(project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) {
      PyInterpreterLibrarySync.getInstance(project).syncForTests()
    }
  }
  job.asPromise().waitForPromiseAndPumpEdt(DEFAULT_TEST_TIMEOUT)
}

/**
 * Installs [packages] in the test manager of the interpreter of [module], next to the packages that it holds, and returns
 * the restore of the previous packages. The bundled stubs of each package then become class roots through the production
 * path, see [com.jetbrains.python.packaging.management.PythonPackageStubRoots]. Each package must have bundled stubs.
 */
@TestOnly
fun installPackagesWithBundledStubsForTests(module: Module, packages: List<PythonPackage>): Runnable {
  for (pythonPackage in packages) {
    Assertions.assertTrue(PythonPackageStubs.stubRootsOf(setOf(pythonPackage.name)).isNotEmpty(),
                          "Stub package root for ${pythonPackage.name} not found")
  }
  val interpreter = checkNotNull(findPythonInterpreterForTests(module)) { "No interpreter for ${module.name}" }
  val manager = PythonPackageManager.forPythonInterpreter(module.project, interpreter)
  check(manager is TestPythonPackageManager) {
    "The stubs follow the installed packages, which only the test manager of a mock SDK lets a test set: $manager"
  }
  val before = manager.listInstalledPackagesSnapshot()
  installPackagesForTests(module, manager, before + packages)
  return Runnable {
    if (!module.isDisposed) installPackagesForTests(module, manager, before)
  }
}

/**
 * Installs [packages] in [manager]. The install publishes `packagesChanged`, and the stub roots follow in the background.
 * The test goes on when the roots follow it, see [awaitDerivedRoots].
 */
private fun installPackagesForTests(module: Module, manager: TestPythonPackageManager, packages: List<PythonPackage>) {
  // First, so it follows the packages that the install publishes: a change that comes before it is there is lost.
  PythonInterpreterRoots.getInstance(module.project)
  manager.installPackages(packages)
  awaitDerivedRoots(module.project)
  syncPythonInterpreterLibraries()
  IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
}
