// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.target.TargetEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.run.target.targetEnvironment
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset

@ApiStatus.Internal
interface PyCustomProcessHandlerProvider {
  /**
   * Tries to create a specific [ProcessHandler] for the provided [process]. Returns `null` if the current provider is not connected to the
   * provided [process].
   */
  fun tryCreateProcessHandler(process: Process,
                              commandLine: String,
                              charset: Charset,
                              pathMapper: PyRemotePathMapper,
                              runWithPty: Boolean): ProcessHandler?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyCustomProcessHandlerProvider> = ExtensionPointName("Pythonid.customProcessHandlerProvider")

    /**
     * Returns a process handler for provided [process]. The process is likely to be a Python script execution.
     *
     * The returned [ProcessHandler] is expected to implement:
     * - [com.jetbrains.python.debugger.PositionConverterProvider] interface to map potentially remote Python scripts positions of debugged
     *   process to the local paths,
     * - [com.intellij.remote.ProcessControlWithMappings] interface to map paths in Python tracebacks in run debug and stacktrace toolwindows.
     *
     * It may also implement [com.jetbrains.python.remote.PyRemoteProcessControl] interface to allow indirect connection to a TCP port on a
     * remote machine for starting a remote debug console and similar functionality.
     */
    @ApiStatus.Internal
    @JvmStatic
    @JvmOverloads
    fun createProcessHandler(process: Process,
                             targetEnvironment: TargetEnvironment,
                             commandLine: String,
                             charset: Charset,
                             pathMapper: PyRemotePathMapper,
                             isMostlySilentProcess: Boolean = false,
                             runWithPty: Boolean = false): ProcessHandler {
      val processHandler = EP_NAME.computeSafeIfAny { it.tryCreateProcessHandler(process, commandLine, charset, pathMapper, runWithPty) }
                           ?: ProcessHandlerWithPyPositionConverter(process, commandLine, charset, pathMapper, isMostlySilentProcess)
      processHandler.targetEnvironment = targetEnvironment
      return processHandler
    }
  }
}