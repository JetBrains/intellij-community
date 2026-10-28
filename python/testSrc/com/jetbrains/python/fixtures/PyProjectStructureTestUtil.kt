// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.python.pyproject.model.evolution.PyInterpreterLibrarySync
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.asPromise

/** Blocking [EvoPyProjectModel.awaitCurrentInterpreters]. Pumps the EDT, so a test on the EDT can call it. */
@TestOnly
fun awaitPythonInterpreters(project: Project) {
  val job = PyPackageCoroutine.getScope(project).launch {
    withTimeout(DEFAULT_TEST_TIMEOUT) {
      EvoPyProjectModel.getInstance(project).awaitCurrentInterpreters()
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
 * Makes the Python project of [module] own the SDK of [module], for a light project of the legacy flow, whose platform
 * adds a new SDK for each test. The SDK then names the interpreter of the Python project, as the registry sets it for an
 * SDK that one module uses. Does nothing in the interpreter library flow, whose registry sets the owner, and for an SDK
 * that the Python project owns already. The caller waits for the interpreters, see [awaitPythonInterpreters].
 */
@TestOnly
fun ownModuleSdkForTests(module: Module) {
  if (isInterpreterLibraryFlowEnabled()) return
  val sdk = ModuleRootManager.getInstance(module).sdk ?: return
  // The base directory of the Python project of a light module is its content root.
  val ownerDir = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: return
  val data = sdk.sdkAdditionalData as? PythonSdkAdditionalData ?: return
  if (data.associatedModulePath == ownerDir) return
  ApplicationManager.getApplication().invokeAndWait {
    WriteAction.run<RuntimeException> {
      val modificator = sdk.sdkModificator
      (modificator.sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath = ownerDir
      modificator.commitChanges()
    }
  }
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
