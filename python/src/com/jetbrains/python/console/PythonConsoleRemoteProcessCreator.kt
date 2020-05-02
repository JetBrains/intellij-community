/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.remote.CredentialsType
import com.intellij.remote.ext.CredentialsCase
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
  @JvmDefault
  @Throws(IOException::class)
  fun createRemoteTunnel(project: Project,
                         data: PyRemoteSdkAdditionalDataBase,
                         localPort: Int): Int? = localPort

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PythonConsoleRemoteProcessCreator<Any>> = ExtensionPointName.create<PythonConsoleRemoteProcessCreator<Any>>(
      "Pythonid.remoteConsoleProcessCreator")
  }
}

data class RemoteConsoleProcessData(val remoteProcessHandlerBase: ProcessHandler,
                                    val pydevConsoleCommunication: PydevConsoleCommunication,
                                    val commandLine: String?,
                                    val process: Process,
                                    val socketProvider: PyRemoteSocketToLocalHostProvider)

@Throws(ExecutionException::class)
fun createRemoteConsoleProcess(commandLine: GeneralCommandLine,
                               pathMapper: PyRemotePathMapper,
                               project: Project,
                               data: PyRemoteSdkAdditionalDataBase,
                               runnerFileFromHelpers: String): RemoteConsoleProcessData {
  val extensions = PythonConsoleRemoteProcessCreator.EP_NAME.extensions
  val result = Ref.create<RemoteConsoleProcessData>()
  val exception = Ref.create<ExecutionException>()

  val collectedCases = extensions.map {
    object : CredentialsCase<Any> {
      override fun getType(): CredentialsType<Any> = it.credentialsType

      override fun process(credentials: Any) {
        try {
          val remoteConsoleProcess = it.createRemoteConsoleProcess(commandLine = commandLine,
                                                                   pathMapper = pathMapper,
                                                                   project = project,
                                                                   data = data,
                                                                   runnerFileFromHelpers = runnerFileFromHelpers,
                                                                   credentials = credentials)
          result.set(remoteConsoleProcess)
        }
        catch (e: ExecutionException) {
          exception.set(e)
        }
      }
    } as CredentialsCase<Any>
  }

  data.switchOnConnectionType(*collectedCases.toTypedArray())
  if (!exception.isNull) {
    throw exception.get()
  }
  else if (!result.isNull) {
    return result.get()
  }
  else {
    throw ExecutionException("Python console for ${data.remoteConnectionType.name} interpreter is not supported")
  }
}