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
package com.jetbrains.python.debugger

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.remote.CidrRemoteDebugParameters
import com.jetbrains.cidr.execution.testing.CidrLauncher
import java.io.File

class PyCidrGDBDebugLauncher(val myProject: Project,
                             val myDebugger : String?,
                             val generalCommandLine: GeneralCommandLine,
                             val consoleBuilder : TextConsoleBuilder,
                             val myEditorProvider: XDebuggerEditorsProvider) : CidrLauncher() {
  override fun getProject() = myProject

  override fun createProcess(state: CommandLineState): ProcessHandler? {
    throw UnsupportedOperationException()
  }

  override fun createDebugProcess(state: CommandLineState, session: XDebugSession): CidrDebugProcess {
    val configuration = GDBDriverConfiguration(CPPToolchains.getInstance().defaultToolchain, myDebugger?.let { File(it) })
    return MixedCidrDebugProcess(configuration,
                                 generalCommandLine,
                                 session,
                                 consoleBuilder,
                                 myEditorProvider)
  }
}