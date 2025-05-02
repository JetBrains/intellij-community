// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.target.value.constant
import com.intellij.openapi.application.PathManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.PythonToolModuleExecution
import com.jetbrains.python.run.PythonToolScriptExecution
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.plusAssign
import kotlin.io.path.pathString

@RequiresBackgroundThread(generateAssertion = false)
fun buildUvRunConfigurationCli(options: UvRunConfigurationOptions, isDebug: Boolean): PythonExecution {
  val toolPath = getUvExecutable()
  val toolParams = mutableListOf("run")

  if (isDebug && !options.uvArgs.contains("--cache-dir")) {
    val pycacheDir = PathManager.getSystemDir().resolve("cpython-cache")
    Files.createDirectories(pycacheDir)
    toolParams += listOf("--cache-dir", pycacheDir.toAbsolutePath().pathString)
  }

  val associatedModuleDirectory = options.uvSdk?.associatedModulePath

  if (!options.uvArgs.contains("--project") && associatedModuleDirectory != null) {
    toolParams += listOf("--project", associatedModuleDirectory)
  }

  toolParams += options.uvArgs

  val execution = when (options.runType) {
    UvRunType.SCRIPT -> PythonToolScriptExecution().apply {
      this.toolParams = if (isDebug && !options.uvArgs.contains("--no-sync")) {
         listOf(
          "run",
          "--no-sync", // initial script helper execution should not sync
          PythonHelpersLocator.findPathStringInHelpers("uv/uv_sync_proxy.py"),
          toolPath?.pathString ?: "uv",
          options.scriptOrModule
        ) + toolParams
      } else if (!isDebug) {
        toolParams + "--script"
      } else {
        toolParams
      }

      this.toolPath = toolPath

      pythonScriptPath = constant(Path.of(options.scriptOrModule))
    }
    UvRunType.MODULE -> PythonToolModuleExecution().apply {
      this.toolPath = toolPath
      this.toolParams = toolParams
      moduleName = options.scriptOrModule
      moduleFlag = "--module"
    }
  }

  execution.addParameters(options.args)
  options.env.forEach { execution.envs.put(it.key, constant(it.value)) }

  return execution
}