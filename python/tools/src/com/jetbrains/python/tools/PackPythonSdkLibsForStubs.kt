// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.sdk.PythonSdkUtil
import java.io.File
import kotlin.system.exitProcess


fun main() {
  val pythons = System.getenv("PACK_STDLIB_FROM")
  val baseDir = System.getenv("PACK_STDLIB_TO")
  if (!File(baseDir).exists()) {
    File(baseDir).mkdirs()
  }

  try {
    for (python in File(pythons).listFiles()!!) {
      if (python.name.startsWith(".")) {
        continue
      }
      val sdkHome = python.absolutePath

      val executable = File(PythonSdkUtil.getPythonExecutable(sdkHome) ?: throw AssertionError("No python on $sdkHome"))
      println("Packing stdlib of $sdkHome")

      val cph = CapturingProcessHandler(GeneralCommandLine(executable.absolutePath, PythonHelper.GENERATOR3.asParamString(), "-u", baseDir))
      val output = cph.runProcess()
      println(output.stdout + output.stderr)
    }
  }
  finally {
    exitProcess(0)
  }
}