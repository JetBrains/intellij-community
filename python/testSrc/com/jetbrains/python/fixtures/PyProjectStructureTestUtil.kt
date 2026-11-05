// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import com.jetbrains.python.codeInsight.typing.PyBundledStubs
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
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

/** A light project with a Python module. The project structure ignores a module of another type. */
@TestOnly
open class PyModuleLightProjectDescriptor(level: LanguageLevel) : PyLightProjectDescriptor(level) {
  override fun getModuleTypeId(): String = PYTHON_MODULE_ID
}

/**
 * Adds the bundled stubs of [packages] to the class roots of the SDK of [module], and returns the removal of the roots.
 * Each package must have bundled stubs. The stub roots do not depend on the version of a package.
 */
@TestOnly
fun installPackagesWithBundledStubsForTests(module: Module, packages: List<PythonPackage>): Runnable {
  val sdk = checkNotNull(PythonSdkUtil.findPythonSdk(module)) { "No SDK for ${module.name}" }
  val stubRoots = packages.map { pythonPackage ->
    checkNotNull(findStubRootForPackage(pythonPackage)) { "Stub package root for ${pythonPackage.name} not found" }
  }
  WriteAction.run<RuntimeException> {
    val modificator = sdk.sdkModificator
    stubRoots.forEach { modificator.addRoot(it, OrderRootType.CLASSES) }
    modificator.commitChanges()
  }
  IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  return Runnable {
    if (module.isDisposed) return@Runnable
    WriteAction.run<RuntimeException> {
      val modificator = sdk.sdkModificator
      stubRoots.forEach { modificator.removeRoot(it, OrderRootType.CLASSES) }
      modificator.commitChanges()
    }
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }
}

/**
 * Finds the typeshed or bundled stub root of [pythonPackage] by the normalized name.
 * [PythonPackage.name] is normalized, but a stub root has the PyPI spelling, for example `SQLAlchemy`.
 */
private fun findStubRootForPackage(pythonPackage: PythonPackage): VirtualFile? =
  sequenceOf(PyTypeShed.thirdPartyStubRoot, PyBundledStubs.root)
    .flatMap { it?.children.orEmpty().asSequence() }
    .firstOrNull { it.isDirectory && PyPackageName.from(it.name) == pythonPackage.normalizedName }
