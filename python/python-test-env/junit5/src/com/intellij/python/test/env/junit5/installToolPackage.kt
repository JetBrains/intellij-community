// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Install [requirement] (a PEP 508 requirement string, e.g. `ruff==0.15.18`) into the interpreter of this project
 * via the real package manager. Retried with a short backoff to tolerate flaky network access on CI.
 *
 * Take [requirement] from [LspToolVersions], so every env test installs the same pinned version.
 */
suspend fun PyProject.installToolPackage(requirement: String) {
  // The venv fixture has just set the interpreter, so wait until the snapshot holds it.
  EvoPyProjectModel.getInstance(project).awaitInterpreterOf(listOf(this))
  val interpreter = requireNotNull(getInterpreter()) { "No interpreter for ${baseDir}" }
  val packageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)
  val spec = PythonRepositoryPackageSpecification(PyPiPackageRepository, PyRequirementParser.fromLine(requirement)!!)
  val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))

  var lastError: Throwable? = null
  repeat(3) { attempt ->
    try {
      val result = packageManager.installPackage(installRequest)
      assertTrue(result.isSuccess) { "Failed to install '$requirement': ${result.errorOrNull}" }
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
      return
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      // Either the install reported failure (AssertionError) or the package manager threw (e.g. a
      // network error); both are worth retrying.
      lastError = e
      if (attempt < 2) delay(2.seconds * (attempt + 1))
    }
  }
  throw lastError!!
}
