// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.ExecutionException
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.flavors.runConda
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.Instance
import java.io.File
import java.nio.file.Path

object CondaEnvCreator {
  @Throws(ExecutionException::class)
  fun createVirtualEnv(
    condaExecutable: String?, destinationDir: String,
    version: String,
  ): String {
    if (condaExecutable == null) {
      throw PyExecutionException(PySdkBundle.message("python.sdk.conda.dialog.cannot.find.conda"))
    }

    val parameters = listOf("create", "-p", destinationDir, "-y", "python=" + version)

    runConda(condaExecutable, parameters)
    val binary = Instance.findPythonInPythonRoot(Path.of(destinationDir))
    val binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python"
    return binary?.toString() ?: binaryFallback
  }
}