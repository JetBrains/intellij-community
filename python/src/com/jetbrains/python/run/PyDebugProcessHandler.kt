// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.io.BaseOutputReader
import java.nio.charset.Charset

class PyDebugProcessHandler : PythonProcessHandler {
  constructor(commandLine: GeneralCommandLine) : super(commandLine)
  constructor(process: Process, commandLine: String, charset: Charset) : super(process, commandLine, charset)

  override fun readerOptions(): BaseOutputReader.Options {
    return BaseOutputReader.Options.forMostlySilentProcess()
  }
}