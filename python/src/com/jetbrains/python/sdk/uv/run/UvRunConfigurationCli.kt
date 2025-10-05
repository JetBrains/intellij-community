// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.target.value.constant
import com.intellij.openapi.application.PathManager
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.PythonToolModuleExecution
import com.jetbrains.python.run.PythonToolScriptExecution
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun buildUvRunConfigurationCli(options: UvRunConfigurationOptions, isDebug: Boolean): PythonExecution {
  val toolPath = getUvExecutable()

  if (toolPath == null) {
    throw RuntimeException("Unable to find uv executable.")
  }

  val toolParams = mutableListOf("run")

  if (isDebug && !options.uvArgs.contains("--cache-dir")) {
    val pycacheDir = PathManager.getSystemDir().resolve("cpython-cache")
    Files.createDirectories(pycacheDir)
    toolParams += listOf("--cache-dir", pycacheDir.toAbsolutePath().pathString)
  }

  val workingDirectory = options.workingDirectory

  if (!options.uvArgs.contains("--project") && workingDirectory != null) {
    toolParams += listOf("--project", workingDirectory.pathString)
  }

  toolParams += options.uvArgs

  val execution = when (options.runType) {
    UvRunType.SCRIPT -> {
      PythonToolScriptExecution(
        toolPath,
        if (isDebug && !options.uvArgs.contains("--no-sync")) {
          listOf(
            "run",
            "--no-sync", // initial script helper execution should not sync
            "--active",
            PythonHelpersLocator.findPathStringInHelpers("uv/uv_sync_proxy.py"),
            toolPath.pathString,
            options.scriptOrModule
          ) + toolParams
        } else if (!isDebug) {
          toolParams + "--script"
        } else {
          toolParams
        },
        pythonScriptPath = constant(Path.of(options.scriptOrModule))
      )
    }
    UvRunType.MODULE -> PythonToolModuleExecution(
      toolPath,
      toolParams,
      options.scriptOrModule,
      "--module"
    )
  }

  execution.addParameters(options.args)
  execution.envs += mapOf(*options.env.map { it.key to constant(it.value) }.toTypedArray())

  return execution
}