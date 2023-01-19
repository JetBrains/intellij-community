// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.execution.ExecutionException
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.createProcessWithResult
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.*
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities

class PyTargetsIntrospectionFacade(val sdk: Sdk, val project: Project) {
  private val pyRequest: HelpersAwareTargetEnvironmentRequest =
    checkNotNull(PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project))

  private val targetEnvRequest: TargetEnvironmentRequest
    get() = pyRequest.targetEnvironmentRequest

  init {
    check(sdk !is Disposable || !Disposer.isDisposed(sdk))
  }

  fun isLocalTarget(): Boolean = targetEnvRequest is LocalTargetEnvironmentRequest


  /**
   * Runs python [command] and returns stdout or error
   */
  suspend fun getCommandOutput(indicator: ProgressIndicator, command: String): Result<String> {
    assert(!SwingUtilities.isEventDispatchThread()) { "Can't run on EDT" }

    val cmdBuilder = TargetedCommandLineBuilder(targetEnvRequest)
    sdk.configureBuilderToRunPythonOnTarget(cmdBuilder)
    cmdBuilder.addParameter("-c")
    cmdBuilder.addParameter(command)
    val cmd = cmdBuilder.build()


    val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    return withContext(Dispatchers.IO) {
      environment.createProcessWithResult(cmd).mapFlat { it.getResultStdoutStr() }
    }
  }

  @Throws(ExecutionException::class)
  fun getInterpreterVersion(indicator: ProgressIndicator): String? {
    // PythonExecution doesn't support launching a bare interpreter without a script or module
    val cmdBuilder = TargetedCommandLineBuilder(targetEnvRequest)
    sdk.configureBuilderToRunPythonOnTarget(cmdBuilder)
    val sdkFlavor = sdk.sdkFlavor
    cmdBuilder.addParameter(sdkFlavor.versionOption)
    val cmd = cmdBuilder.build()

    val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    return sdkFlavor.getVersionStringFromOutput(cmd.execute(environment, indicator))
  }

  @Throws(ExecutionException::class)
  fun getInterpreterPaths(indicator: ProgressIndicator): List<String> {
    val execution = prepareHelperScriptExecution(helperPackage = PythonHelper.SYSPATH, helpersAwareTargetRequest = pyRequest)
    val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    val cmd = execution.buildTargetedCommandLine(environment, sdk, emptyList())
    return cmd.execute(environment, indicator).stdoutLines
  }

  @Throws(ExecutionException::class)
  fun synchronizeRemoteSourcesAndSetupMappings(indicator: ProgressIndicator) {
    if (isLocalTarget()) return
    PyTargetsRemoteSourcesRefresher(sdk, project).run(indicator)
  }
}