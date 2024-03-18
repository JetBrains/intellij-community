// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains

import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.*
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert


internal suspend fun getPythonVersion(sdk: Sdk, request: TargetEnvironmentRequest): String? {
  val commandLineBuilder = TargetedCommandLineBuilder(request)
  sdk.configureBuilderToRunPythonOnTarget(commandLineBuilder)
  val flavor = sdk.getOrCreateAdditionalData().flavor
  return getPythonVersion(commandLineBuilder, flavor, request)
}

internal suspend fun getPythonVersion(commandLineBuilder: TargetedCommandLineBuilder,
                                      flavor: PythonSdkFlavor<*>,
                                      request: TargetEnvironmentRequest): String? {
  commandLineBuilder.addParameter(flavor.versionOption)
  val commandLine = commandLineBuilder.build()
  val result = request
    .prepareEnvironment(TargetProgressIndicator.EMPTY)
    .createProcess(commandLine).getBareExecutionResult()

  // Conda python may send version to stderr, check both

  val err = result.stdErr.decodeToString()
  val out = result.stdOut.decodeToString()
  Assert.assertEquals(err, 0, result.exitCode)
  val versionString = out.ifBlank { err }.trim()
  return flavor.getVersionStringFromOutput(versionString)
}

/**
 * @return path to python binary on target
 */
suspend fun Sdk.getPythonBinaryPath(project: Project): Result<FullPathOnTarget> {
  return getCommandOutput(project, EmptyProgressIndicator(), "import sys; print(sys.executable)").map { it.trim() }
}

/**
 * Runs python [command] and returns stdout or error
 */
private suspend fun Sdk.getCommandOutput(project: Project, indicator: ProgressIndicator, command: String): Result<String> {
  ThreadingAssertions.assertBackgroundThread()

  val pyRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk = this, project)
  val targetEnvRequest = pyRequest.targetEnvironmentRequest

  val cmdBuilder = TargetedCommandLineBuilder(targetEnvRequest)
  configureBuilderToRunPythonOnTarget(cmdBuilder)
  cmdBuilder.addParameter("-c")
  cmdBuilder.addParameter(command)
  val cmd = cmdBuilder.build()

  val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
  return withContext(Dispatchers.IO) {
    environment.createProcessWithResult(cmd).mapFlat { it.getResultStdoutStr() }
  }
}