// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.execution.ExecutionException
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.annotations.paths.OsPath
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelper.Constants.SYSPATH_PY
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.executeHelper
import org.jetbrains.annotations.ApiStatus

/**
 * Gets information about a Python interpreter through the Targets API.
 * Supports a native interpreter and an interpreter on a target.
 * Use [create] to get an instance, and use the instance polymorphically: for a native interpreter, some methods do
 * nothing. To check if the interpreter is local, use [isLocalTarget].
 */
@ApiStatus.Internal
sealed class PyTargetsIntrospectionFacade(protected val interpreter: PythonInterpreter) {
  protected val project: Project
    get() = interpreter.project

  companion object {
    /**
     * @throws com.jetbrains.python.sdk.InvalidSdkException if [interpreter] is on a target and the plugin for its target is not loaded
     */
    @Throws(InvalidSdkException::class)
    @JvmStatic
    fun create(interpreter: PythonInterpreter): PyTargetsIntrospectionFacade = when (interpreter) {
      is NativePythonInterpreter -> PyTargetsIntrospectionFacadeLocal(interpreter)
      is TargetPythonInterpreter -> PyTargetsIntrospectionFacadeRemote.create(interpreter)
                                    ?: throw InvalidSdkException(PyBundle.message("python.sdk.target.no.plugin",
                                                                                  interpreter.targetConfiguration::class.java.name))
    }

    @JvmStatic
    @Throws(ExecutionException::class)
    protected fun <T> PyResult<T>.orThrowExecException(): T = getOr { throw ExecutionException(it.error.message) }
  }

  abstract val isLocalTarget: Boolean

  @RequiresBackgroundThread(generateAssertion = false)
  @kotlin.jvm.Throws(ExecutionException::class)
  open fun getInterpreterPaths(): List<@OsPath String> = runBlockingMaybeCancellable {
    ExecService().executeHelper(interpreter, PyHelper(SYSPATH_PY)).orThrowExecException()
  }.lines().filter { it.isNotBlank() }


  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @Throws(ExecutionException::class)
  abstract fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator)

}
