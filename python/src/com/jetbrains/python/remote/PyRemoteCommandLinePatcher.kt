// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParamsGroup
import com.intellij.remote.RemoteSdkException
import com.intellij.remote.RemoteSdkPropertiesPaths
import com.jetbrains.python.remote.PyRemoteCommandLineStateUtil.*
import com.jetbrains.python.run.PythonCommandLineState.GROUP_COVERAGE
import com.jetbrains.python.run.PythonCommandLineState.GROUP_DEBUGGER
import com.jetbrains.python.run.PythonCommandLineState.GROUP_PROFILER

/**
 * Supports debug, profile and coverage command lines. Patches em to make them usable on remote interpreters
 */
@Throws(RemoteSdkException::class)
fun GeneralCommandLine.patchRemoteCommandLineIfNeeded(sdkData: RemoteSdkPropertiesPaths,
                                                      socketProvider: PyRemoteSocketToLocalHostProvider,
                                                      pathMapper: PyRemotePathMapper) {
  val helpersPath = sdkData.helpersPath
  val interpreterPath = sdkData.interpreterPath
  val patchers = linkedMapOf<String, (ParamsGroup) -> Unit>( //Order is important
    GROUP_DEBUGGER to { params -> patchDebugParams(helpersPath, socketProvider, params) },
    GROUP_PROFILER to { params ->
      patchProfileParams(interpreterPath, socketProvider, params, workDirectory, pathMapper)
    },
    GROUP_COVERAGE to { params -> patchCoverageParams(interpreterPath, params, workDirectory, pathMapper) }
  )

  for ((group, function) in patchers) {
    val params = parametersList.getParamsGroup(group) ?: continue
    if (params.parametersList.list.isNotEmpty()) {
      function(params)
    }
  }
}
