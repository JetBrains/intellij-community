@file:JvmName("PydevConsoleCli")

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParamsGroup
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonCommandLineState

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
fun GeneralCommandLine.setupPythonConsoleScriptInClientMode(port: Int) {
  initializePydevConsoleScriptGroup().appendClientModeParameters(port)
}

/**
 * Adds new or replaces existing [PythonCommandLineState.GROUP_SCRIPT]
 * parameters of [this] command line with the path to Python console script
 * (*pydevconsole.py*) and parameters required for running it in the *server*
 * mode.
 *
 * @param port the optional port that Python console script will listen at
 *
 * @see PythonHelper.CONSOLE
 */
@JvmOverloads
fun GeneralCommandLine.setupPythonConsoleScriptInServerMode(port: Int? = null) {
  initializePydevConsoleScriptGroup().appendServerModeParameters(port)
}

private fun GeneralCommandLine.initializePydevConsoleScriptGroup(): ParamsGroup {
  val group: ParamsGroup = parametersList.getParamsGroup(PythonCommandLineState.GROUP_SCRIPT)?.apply { parametersList.clearAll() }
                           ?: parametersList.addParamsGroup(PythonCommandLineState.GROUP_SCRIPT)

  PythonHelper.CONSOLE.addToGroup(group, this)

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