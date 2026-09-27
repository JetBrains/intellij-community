// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PYTHON_VERSION_ARG
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelper.Constants.SYSPATH_PY
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.execGetStdout
import com.jetbrains.python.sdk.executeHelper
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

/**
 * Gets information about a Python SDK through the Targets API.
 * Supports local SDKs and remote SDKs (SDKs with [PyTargetAwareAdditionalData]).
 * Use [create] to get an instance, and use the instance polymorphically: for a local SDK, some methods do nothing.
 * To check if the SDK is local, use [isLocalTarget].
 */
@ApiStatus.Internal
sealed class PyTargetsIntrospectionFacade(
  protected val sdk: Sdk,
  protected val project: Project,
) {
  companion object {
    /**
     * @throws com.jetbrains.python.sdk.InvalidSdkException if [sdk] is remote and the plugin for its target is not loaded
     */
    @Throws(InvalidSdkException::class)
    @JvmStatic
    fun create(sdk: Sdk, project: Project): PyTargetsIntrospectionFacade {
      val targetData = sdk.sdkAdditionalData as? PyTargetAwareAdditionalData
      return if (targetData != null) {
        PyTargetsIntrospectionFacadeRemote.create(sdk, targetData, project)
        ?: throw InvalidSdkException(PyBundle.message("python.sdk.target.no.plugin", targetData::class.java.name))
      }
      else {
        PyTargetsIntrospectionFacadeLocal(sdk, project)
      }
    }

    @JvmStatic
    @Throws(ExecutionException::class)
    protected fun <T> PyResult<T>.orThrowExecException(): T = getOr { throw ExecutionException(it.error.message) }
  }

  protected suspend fun interpreter(): PythonInterpreter = sdk.pythonInterpreterAsync()


  abstract val isLocalTarget: Boolean

  @Throws(ExecutionException::class)
  @RequiresBackgroundThread
  fun getInterpreterVersion(): String? =
    runBlockingMaybeCancellable {
      PythonSdkFlavor.getVersionStringFromOutput(
        ExecService().execGetStdout(interpreter(), Args(PYTHON_VERSION_ARG))
          .orThrowExecException()
      )
    }

  @RequiresBackgroundThread(generateAssertion = false)
  @kotlin.jvm.Throws(ExecutionException::class)
  open fun getInterpreterPaths(): List<String> = runBlockingMaybeCancellable {
    ExecService().executeHelper(interpreter(), PyHelper(SYSPATH_PY)).orThrowExecException()
  }.lines().filter { it.isNotBlank() }


  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @Throws(ExecutionException::class)
  abstract fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator)

}
