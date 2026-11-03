// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/** An update that a user asked for: it reads the packages too. */
private val USER_UPDATE = PythonInterpreterUpdateRequest(packages = true, background = false)

/**
 * Updates [interpreter] in [project] and waits until its facts are read, see [PythonInterpreterUpdater.update]. The
 * skeletons and the other later steps run on in the background.
 */
@ApiStatus.Internal
suspend fun updateInterpreterFacts(interpreter: PythonInterpreter, project: Project): PyResult<Unit> =
  PythonInterpreterUpdater.getInstance(project).update(interpreter, USER_UPDATE)

/** [updateInterpreterFactsOrShowError] in the background of [project], for a Java caller on the EDT. */
@ApiStatus.Internal
fun launchUpdateOrShowError(sdk: Sdk, project: Project) {
  PyPackageCoroutine.launch(project) {
    val interpreter = PythonInterpreterRegistry.getInstance(project).interpreterOf(sdk) ?: return@launch
    updateInterpreterFactsOrShowError(interpreter, project)
  }
}

/** [updateInterpreterFacts], and an error dialog when the facts cannot be read. */
@ApiStatus.Internal
suspend fun updateInterpreterFactsOrShowError(interpreter: PythonInterpreter, project: Project) {
  if (updateInterpreterFacts(interpreter, project) !is Result.Failure) return
  withContext(Dispatchers.EDT) {
    Messages.showErrorDialog(
      project,
      PyBundle.message("python.sdk.cannot.setup.sdk", interpreter.getSdkAPI().name),
      PyBundle.message("python.sdk.invalid.python.sdk"),
    )
  }
}
