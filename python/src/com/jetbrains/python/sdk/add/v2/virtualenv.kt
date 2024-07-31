// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.target.HelpersAwareLocalTargetEnvironmentRequest
import com.jetbrains.python.sdk.PySdkSettings
import java.nio.file.Path

@RequiresEdt // There is runBlockingModel down the code
fun createVirtualenv(
  baseInterpreterPath: String,
  venvRoot: String,
  projectBasePath: Path,
  inheritSitePackages: Boolean = false,
) {

  // todo find request for targets (need sdk, maybe can work around it )
  //PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter()
  val request = HelpersAwareLocalTargetEnvironmentRequest()
  //val targetRequest = request.targetEnvironmentRequest

  val execution = prepareHelperScriptExecution(PythonHelper.VIRTUALENV_ZIPAPP, request) // todo what about legacy pythons?
  if (inheritSitePackages) {
    execution.addParameter("--system-site-packages")
  }

  execution.addParameter(venvRoot)
  request.preparePyCharmHelpers()

  val targetEnvironment = request.targetEnvironmentRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)

  //val targetedCommandLine = execution.buildTargetedCommandLine(targetEnvironment, sdk = null, emptyList())


  val commandLineBuilder = TargetedCommandLineBuilder(targetEnvironment.request)
  commandLineBuilder.setWorkingDirectory(projectBasePath.toString())

  commandLineBuilder.setExePath(baseInterpreterPath)

  execution.pythonScriptPath?.let { commandLineBuilder.addParameter(it.apply(targetEnvironment)) }
  ?: throw IllegalArgumentException("Python script path must be set")

  execution.parameters.forEach { parameter ->
    val resolvedParameter = parameter.apply(targetEnvironment)
    if (resolvedParameter != PythonExecution.SKIP_ARGUMENT) {
      commandLineBuilder.addParameter(resolvedParameter)
    }
  }


  for ((name, value) in execution.envs) {
    commandLineBuilder.addEnvironmentVariable(name, value.apply(targetEnvironment))
  }

  val targetedCommandLine = commandLineBuilder.build()


  // todo poerty/pipenv
  //val targetedCommandLineBuilder = TargetedCommandLineBuilder(request.targetEnvironmentRequest)
  //targetedCommandLineBuilder.exePath = TargetValue.fixed("")
  //val targetedCommandLine = targetedCommandLineBuilder.build()

  val process = targetEnvironment.createProcess(targetedCommandLine)

  val handler = CapturingProcessHandler(process, targetedCommandLine.charset, targetedCommandLine.getCommandPresentation(targetEnvironment))

  val output = runWithModalProgressBlocking(ModalTaskOwner.guess(), "creating venv") {
    handler.runProcess(60 * 1000)
  }

  PySdkSettings.instance.preferredVirtualEnvBaseSdk = baseInterpreterPath
}