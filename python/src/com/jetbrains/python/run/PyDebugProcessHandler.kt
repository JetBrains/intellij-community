// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.io.BaseOutputReader

class PyDebugProcessHandler(commandLine: GeneralCommandLine) : PythonProcessHandler(commandLine) {

  override fun readerOptions(): BaseOutputReader.Options {
    return BaseOutputReader.Options.forMostlySilentProcess()
  }
}