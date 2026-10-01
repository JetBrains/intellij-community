// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
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
