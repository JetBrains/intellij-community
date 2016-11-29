/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.process.ProcessHandler
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.python.console.PydevConsoleExecuteActionHandler
import com.jetbrains.python.console.pydev.ConsoleCommunication

/**
 * @author traff
 */
class PydevDebugConsoleExecuteActionHandler(consoleView: LanguageConsoleView,
                                            myProcessHandler: ProcessHandler,
                                            consoleCommunication: ConsoleCommunication) : PydevConsoleExecuteActionHandler(consoleView, myProcessHandler, consoleCommunication), XDebugSessionListener {

  override val consoleIsNotEnabledMessage: String
    get() = "Pause the process to use command-line."

  override fun sessionPaused() {
    isEnabled = true
  }

  override fun sessionResumed() {
    isEnabled = false
  }

  override fun sessionStopped() {
    isEnabled = false
  }

  override fun stackFrameChanged() {
  }

  override fun beforeSessionResume() {
  }
}
