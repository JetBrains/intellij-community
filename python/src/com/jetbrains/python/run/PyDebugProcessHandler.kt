// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.io.awaitExit
import com.jetbrains.python.debugger.PythonDebuggerScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.Charset

class PyDebugProcessHandler : PythonProcessHandler {
  constructor(commandLine: GeneralCommandLine) : super(commandLine)
  constructor(process: Process, commandLine: String, charset: Charset) : super(process, commandLine, charset)

  override fun readerOptions(): BaseOutputReader.Options {
    return if (hasPty()) {
      BaseOutputReader.Options.forTerminalPtyProcess()
    } else {
      BaseOutputReader.Options.forMostlySilentProcess()
    }
  }

  protected override fun doDestroyProcess() {
    super.doDestroyProcess()

    PythonDebuggerScope.launchOn(Dispatchers.IO) {
      withTimeoutOrNull(1000) {
        process.awaitExit()
      } ?: if (shouldDestroyProcessRecursively() && processCanBeKilledByOS(process)) {
        killProcessTree(process)
      }
      else {
        process.destroy()
      }
    }
  }
}