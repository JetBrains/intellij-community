// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.remote.CredentialsType
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.remote.PyRemoteSocketToLocalHostProvider
import java.io.IOException

interface PythonConsoleRemoteProcessCreator<T> {
  val credentialsType: CredentialsType<T>

  @Throws(ExecutionException::class)
  fun createRemoteConsoleProcess(commandLine: GeneralCommandLine,
                                 pathMapper: PyRemotePathMapper,
                                 project: Project,
                                 data: PyRemoteSdkAdditionalDataBase,
                                 runnerFileFromHelpers: String,
                                 credentials: T): RemoteConsoleProcessData

  /**
   * Tries to create a remote tunnel.
   * @return Port on the remote server or null if port forwarding by this method is not implemented.
   */
  @Throws(IOException::class)
  fun createRemoteTunnel(project: Project,
                         data: PyRemoteSdkAdditionalDataBase,
                         localPort: Int): Int? = localPort

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PythonConsoleRemoteProcessCreator<Any>> = ExtensionPointName.create(
      "Pythonid.remoteConsoleProcessCreator")
  }
}

data class RemoteConsoleProcessData(val pydevConsoleCommunication: PydevConsoleCommunication,
                                    val commandLine: String,
                                    val process: Process,
                                    val socketProvider: PyRemoteSocketToLocalHostProvider)

