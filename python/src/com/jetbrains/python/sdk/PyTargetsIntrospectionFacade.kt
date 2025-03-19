// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.*
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

class PyTargetsIntrospectionFacade(val sdk: Sdk, val project: Project) {
  private val pyRequest: HelpersAwareTargetEnvironmentRequest =
    checkNotNull(PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project))

  private val targetEnvRequest: TargetEnvironmentRequest
    get() = pyRequest.targetEnvironmentRequest

  init {
    check(sdk !is Disposable || !Disposer.isDisposed(sdk))
  }

  fun isLocalTarget(): Boolean = targetEnvRequest is LocalTargetEnvironmentRequest

  @Throws(ExecutionException::class)
  fun getInterpreterVersion(indicator: ProgressIndicator): String? {
    // PythonExecution doesn't support launching a bare interpreter without a script or module
    val cmdBuilder = TargetedCommandLineBuilder(targetEnvRequest)
    sdk.configureBuilderToRunPythonOnTarget(cmdBuilder)
    val sdkFlavor = sdk.sdkFlavor
    cmdBuilder.addParameter(PythonSdkFlavor.PYTHON_VERSION_ARG)
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

  @Throws(ExecutionException::class)
  fun synchronizeRemoteSourcesAndSetupMappings(indicator: ProgressIndicator) {
    if (isLocalTarget()) return
    PyTargetsRemoteSourcesRefresher(sdk, project).run(indicator)
  }
}