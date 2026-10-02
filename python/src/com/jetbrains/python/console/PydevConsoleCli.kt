// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PydevConsoleCli")

package com.jetbrains.python.console

import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

@ApiStatus.Internal const val MODE_OPTION = "mode"
@ApiStatus.Internal const val MODE_OPTION_CLIENT_VALUE = "client"

@ApiStatus.Internal const val HOST_OPTION = "host"
@ApiStatus.Internal const val PORT_OPTION = "port"

private fun getOptionString(name: String, value: Any): String = "--$name=$value"

private fun getOptionString(name: String, value: TargetEnvironmentFunction<*>): TargetEnvironmentFunction<String> =
  value.andThen { it: Any? -> "--$name=$it" }

/**
 * @param ideServerPort           the host and port where the IDE being Python
 *                                Console frontend listens for the connection
 */
internal fun createPythonConsoleScriptInClientMode(ideServerPort: Function<TargetEnvironment, HostPort>,
                                          helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): PythonExecution {
  val pythonScriptExecution = prepareHelperScriptExecution(PythonHelper.CONSOLE, helpersAwareTargetRequest)
  pythonScriptExecution.addParameter(getOptionString(MODE_OPTION, MODE_OPTION_CLIENT_VALUE))
  pythonScriptExecution.addParameter(getOptionString(HOST_OPTION, ideServerPort.andThen(HostPort::host)))
  pythonScriptExecution.addParameter(getOptionString(PORT_OPTION, ideServerPort.andThen(HostPort::port)))
  return pythonScriptExecution
}