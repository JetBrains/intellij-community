// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains

import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.getOrCreateAdditionalData
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