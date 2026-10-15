// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.psi.LanguageLevel
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

