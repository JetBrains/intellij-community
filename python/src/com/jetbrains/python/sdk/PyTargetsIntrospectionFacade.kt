// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.getInterpreterPath
import com.jetbrains.python.run.prepareHelperScriptExecution

class PyTargetsIntrospectionFacade(val mySdk: Sdk) {
  private val myTargetEnvRequest = checkNotNull(PythonInterpreterTargetEnvironmentFactory.findTargetEnvironmentRequest(mySdk))

  init {
    check(mySdk !is Disposable || !Disposer.isDisposed(mySdk))
  }

  fun isLocalTarget(): Boolean = myTargetEnvRequest is LocalTargetEnvironmentRequest

  fun getInterpreterVersion(indicator: ProgressIndicator): String? {
    // PythonExecution doesn't support launching a bare interpreter without a script or module
    val cmdBuilder = TargetedCommandLineBuilder(myTargetEnvRequest)
    val interpreterPath = getInterpreterPath(mySdk)
    if (interpreterPath.isNullOrEmpty()) {
      throw IllegalStateException("Interpreter path is not configured")
    }
    cmdBuilder.setExePath(interpreterPath)
    val sdkFlavor = mySdk.sdkFlavor
    if (sdkFlavor == null) {
      throw IllegalStateException("SDK flavor is not recognized")
    }
    cmdBuilder.addParameter(sdkFlavor.versionOption)
    val cmd = cmdBuilder.build()

    val environment = myTargetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    val process = environment.createProcess(cmd, indicator)
    val cmdPresentation = cmd.getCommandPresentation(environment)
    val capturingHandler = CapturingProcessHandler(process, cmd.charset, cmdPresentation)
    val stdout = capturingHandler.runProcess().stdout
    return sdkFlavor.getVersionStringFromOutput(stdout)
  }

  fun getInterpreterPaths(indicator: ProgressIndicator): List<String> {
    val execution = prepareHelperScriptExecution(helperPackage = PythonHelper.SYSPATH, targetEnvironmentRequest = myTargetEnvRequest)
    val environment = myTargetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
    val cmd = execution.buildTargetedCommandLine(environment, mySdk, emptyList())
    val process = environment.createProcess(cmd, indicator)
    val cmdPresentation = cmd.getCommandPresentation(environment)
    val capturingHandler = CapturingProcessHandler(process, cmd.charset, cmdPresentation)
    return capturingHandler.runProcess().stdoutLines
  }

  fun synchronizeRemoteSourcesAndSetupMappings(indicator: ProgressIndicator) {
    if (isLocalTarget()) return
    PyTargetsRemoteSourcesRefresher(mySdk).run(indicator)
  }
}