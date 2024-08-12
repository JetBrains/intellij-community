// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.jetbrains.python.PythonHelper
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.VirtualEnvReader
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.io.path.Path


fun main() {
  val pythons = System.getenv("PACK_STDLIB_FROM")
  val baseDir = System.getenv("PACK_STDLIB_TO")
  if (!File(baseDir).exists()) {
    File(baseDir).mkdirs()
  }

  for (python in File(pythons).listFiles()!!) {
    if (python.name.startsWith(".")) {
      continue
    }
    val sdkHome = python.absolutePath

    val executable =  VirtualEnvReader.Instance.findPythonInPythonRoot(Path(sdkHome))?.toFile()

    if (executable == null) {
      println("No python on $sdkHome")
      continue
    }
    else {
      println("Packing stdlib of $sdkHome")
    }

    val arg = PythonHelper.GENERATOR3.asParamString()
    val process = ProcessBuilder(executable.absolutePath, arg, "-u", baseDir).start()

    BufferedReader(InputStreamReader(process.inputStream)).use {
      it.lines().forEach(::println)
    }

    BufferedReader(InputStreamReader(process.errorStream)).use {
      it.lines().forEach(::println)
    }

    val rc = process.waitFor()
    if (rc != 0) {
      error("Process '${executable.absolutePath} $arg' exited with error code $rc")
    }
  }
}