@file:JvmName("PydevConsoleCli")

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParamsGroup
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.io.File

const val MODE_OPTION = "mode"
const val MODE_OPTION_SERVER_VALUE = "server"
const val MODE_OPTION_CLIENT_VALUE = "client"

const val PORT_OPTION = "port"

private fun getOptionString(name: String, value: Any): String = "--$name=$value"

/**
 * Adds new or replaces existing [PythonCommandLineState.GROUP_SCRIPT]
 * parameters of [this] command line with the path to Python console script
 * (*pydevconsole.py*) and parameters required for running it in the *client*
 * mode.
 *
 * @param port the port that Python console script will connect to
 *
 * @see PythonHelper.CONSOLE
 */
fun GeneralCommandLine.setupPythonConsoleScriptInClientMode(sdk: Sdk, port: Int) {
  initializePydevConsoleScriptGroup(PythonSdkFlavor.getFlavor(sdk)).appendClientModeParameters(port)
}

/**
 * Adds new or replaces existing [PythonCommandLineState.GROUP_SCRIPT]
 * parameters of [this] command line with the path to Python console script
 * (*pydevconsole.py*) and parameters required for running it in the *server*
 * mode.
 *
 * Updates Python path according to the flavor defined in [sdkAdditionalData].
 *
 * @param sdkAdditionalData the additional data where [PythonSdkFlavor] is taken
 *                          from
 * @param port the optional port that Python console script will listen at
 *
 * @see PythonHelper.CONSOLE
 */
@JvmOverloads
fun GeneralCommandLine.setupPythonConsoleScriptInServerMode(sdkAdditionalData: SdkAdditionalData, port: Int? = null) {
  initializePydevConsoleScriptGroup((sdkAdditionalData as? PythonSdkAdditionalData)?.flavor).appendServerModeParameters(port)
}

private fun GeneralCommandLine.initializePydevConsoleScriptGroup(pythonSdkFlavor: PythonSdkFlavor?): ParamsGroup {
  val group: ParamsGroup = parametersList.getParamsGroup(PythonCommandLineState.GROUP_SCRIPT)?.apply { parametersList.clearAll() }
                           ?: parametersList.addParamsGroup(PythonCommandLineState.GROUP_SCRIPT)

  val pythonPathEnv = hashMapOf<String, String>()
  PythonHelper.CONSOLE.addToPythonPath(pythonPathEnv)
  val consolePythonPath = pythonPathEnv[PythonEnvUtil.PYTHONPATH]
  // here we get Python console path for the system interpreter
  // let us convert it to the project interpreter path
  consolePythonPath?.split(File.pathSeparator)?.let { pythonPathList ->
    pythonSdkFlavor?.initPythonPath(pythonPathList, false, environment) ?: PythonEnvUtil.addToPythonPath(environment, pythonPathList)
  }
  group.addParameter(PythonHelper.CONSOLE.asParamString())

  return group
}

private fun ParamsGroup.appendServerModeParameters(port: Int? = null) {
  addParameter(getOptionString(MODE_OPTION, MODE_OPTION_SERVER_VALUE))
  port?.let { addParameter(getOptionString(PORT_OPTION, it)) }
}

private fun ParamsGroup.appendClientModeParameters(port: Int) {
  addParameter(getOptionString(MODE_OPTION, MODE_OPTION_CLIENT_VALUE))
  addParameter(getOptionString(PORT_OPTION, port))
}

/**
 * Waits for Python console server to be started. The indication for this is
 * the server port that Python console script outputs to *stdout* when the
 * server socket is bound to the port and it is listening to it.
 *
 * The connection to Python console script server should be established *after*
 * this method finishes.
 *
 * @throws ExecutionException if timeout occurred or an other error
 *
 * @see PydevConsoleRunnerImpl.PORTS_WAITING_TIMEOUT
 * @see PydevConsoleRunnerImpl.getRemotePortFromProcess
 */
@Throws(ExecutionException::class)
fun waitForPythonConsoleServerToBeStarted(process: Process) {
  PydevConsoleRunnerImpl.getRemotePortFromProcess(process)
}