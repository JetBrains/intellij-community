// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.checkers

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.openapi.util.Key
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal abstract class PyStubsChecker(val project: Project) : Disposable.Default {
  /** The stubs suggested for an interpreter, in the user data of its [PythonPackageManager]. */
  private val key: Key<Set<PyPackageStubLink>> = Key(this::class.java.name)

  init {
    project.messageBus.connect(this).subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(interpreter: PythonInterpreter) {
        PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
          check(PythonPackageManager.forPythonInterpreter(interpreter))
        }
      }

    })

    // Before the first snapshot nothing is checked here. The package reload of each interpreter fires the listener above.
    EvoPyProjectModel.getInstance(project).snapshotOrNull()?.interpreters?.forEach { startCheck(it) }
  }

  /** The stubs suggested for [interpreter], or none before the first check. */
  fun getCached(interpreter: PythonInterpreter): Set<PyPackageStubLink> =
    PythonPackageManager.forPythonInterpreter(interpreter).getUserData(key) ?: emptySet()

  protected abstract suspend fun detectSuggestedStubs(packageManager: PythonPackageManager): List<PyPackageStubLink>


  private fun startCheck(interpreter: PythonInterpreter) {
    if (PythonPackageManager.forPythonInterpreter(interpreter).getUserData(key) != null) return

    if (ApplicationManager.getApplication().isUnitTestMode) {
      runBlockingMaybeCancellable {
        check(PythonPackageManager.forPythonInterpreter(interpreter))
      }
    }
    else {
      PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        check(PythonPackageManager.forPythonInterpreter(interpreter))
      }
    }
  }

  /** Stores the stubs [packageManager] suggests for its interpreter, where [getCached] reads them. */
  private suspend fun check(packageManager: PythonPackageManager) {
    val suggested = detectSuggestedStubs(packageManager).toSet()
    packageManager.putUserData(key, suggested)
  }
}
