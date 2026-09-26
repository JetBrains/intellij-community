// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PYTHON_VERSION_ARG
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.execute
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.sdkFlavor
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
  protected val pyRequest: HelpersAwareTargetEnvironmentRequest,
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
        PyTargetsIntrospectionFacadeRemote.create(sdk, targetData, project) ?: throw InvalidSdkException(PyBundle.message("python.sdk.target.no.plugin", targetData::class.java.name))
      }
      else {
        PyTargetsIntrospectionFacadeLocal(sdk, project)
      }
    }
  }

  protected val targetEnvRequest: TargetEnvironmentRequest
    get() = pyRequest.targetEnvironmentRequest

  abstract val isLocalTarget: Boolean

  @Throws(ExecutionException::class)
  fun getInterpreterVersion(indicator: ProgressIndicator): String? {
    // PythonExecution doesn't support launching a bare interpreter without a script or module
    val cmdBuilder = TargetedCommandLineBuilder(targetEnvRequest)
    sdk.configureBuilderToRunPythonOnTarget(cmdBuilder)
    sdk.sdkFlavor
    cmdBuilder.addParameter(PYTHON_VERSION_ARG)
    val cmd = cmdBuilder.build()

    val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    return PythonSdkFlavor.getVersionStringFromOutput(cmd.execute(environment, indicator))
  }

  @Throws(ExecutionException::class)
  fun getInterpreterPaths(indicator: ProgressIndicator): List<String> {
    val execution = prepareHelperScriptExecution(helperPackage = PythonHelper.SYSPATH, helpersAwareTargetRequest = pyRequest)
    val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    val cmd = execution.buildTargetedCommandLine(environment, sdk, emptyList())
    return cmd.execute(environment, indicator).stdoutLines
  }

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @Throws(ExecutionException::class)
  abstract fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator)
}
