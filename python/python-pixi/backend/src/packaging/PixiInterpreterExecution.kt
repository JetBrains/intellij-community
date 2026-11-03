// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.project.ProjectManager
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.execService.toEnvVars
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.asBinToExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal suspend fun executePixiInterpreter(
  interpreter: PythonInterpreter,
  arguments: Args,
  environment: Map<String, String> = emptyMap(),
  workingDirectory: Path? = null,
  uploadConfig: UploadConfig = UploadConfig(ensureWorkingDirectoryExists = false),
): PyResult<String> = withContext(Dispatchers.IO) {
  val directory = workingDirectory ?: pixiInterpreterWorkingDirectory(interpreter, null).getOr { return@withContext it }
  val runtime = PyToolRuntime(
    interpreter.asBinToExecute().getOr { return@withContext it },
    ExecOptions(env = environment.toEnvVars(), uploadBeforeExecution = uploadConfig),
  ).let { if (directory == null) it else it.withWorkingDirectory(directory) }
  ExecService().execGetStdout(runtime.binary, arguments, runtime.execOptions)
}

@RequiresBackgroundThread
internal fun pixiInterpreterWorkingDirectory(interpreter: PythonInterpreter, sourceDirectory: Path?): PyResult<Path?> {
  return when (val binary = interpreter.asBinToExecute().getOr { return it }) {
    is BinOnEel -> PyResult.success(sourceDirectory)
    is BinOnTarget -> {
      val projects = ProjectManager.getInstance()
      val request = binary.target.createEnvironmentRequest(projects.openProjects.firstOrNull() ?: projects.defaultProject)
      val roots = request.uploadVolumes.map { it.localRootPath }.distinct()
      when {
        roots.isEmpty() -> PyResult.success(sourceDirectory)
        roots.size == 1 && (sourceDirectory == null || sourceDirectory.startsWith(roots.single())) -> PyResult.success(roots.single())
        else -> PyResult.localizedError(PyPixiBackendBundle.message(
          if (sourceDirectory == null) "python.pixi.packaging.target.mapping.required"
          else "python.pixi.packaging.source.target.mapping.required",
        ))
      }
    }
  }
}
