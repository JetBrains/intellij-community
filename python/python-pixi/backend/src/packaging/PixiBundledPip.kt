// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

internal suspend fun executeBundledPixiPip(
  interpreter: PythonInterpreter,
  arguments: Args,
  script: String = RUN_PIP,
  workingDirectory: Path? = null,
  uploadConfig: UploadConfig? = null,
): PyResult<String> = withContext(Dispatchers.IO) {
  val wheel = try {
    Path.of(PythonHelper.PACKAGING_TOOL.asParamString()).parent.listDirectoryEntries("pip-*.whl").singleOrNull()
  }
  catch (_: IOException) {
    null
  } ?: return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.bundled.pip.unavailable"))
  val bootstrap = "import sys\nsys.path.insert(0, sys.argv.pop(1))\nimport pip\nsys.path.pop(0)\n"
  val args = Args("-c", bootstrap + script)
  args.addPath(wheel)
  args.add(arguments)
  executePixiInterpreter(interpreter, args, mapOf("PYTHONIOENCODING" to "utf-8", "PIP_REQUIRE_VIRTUALENV" to "false"),
                         workingDirectory, uploadConfig ?: UploadConfig(ensureWorkingDirectoryExists = false))
}

private val RUN_PIP = """
  import runpy
  sys.argv[0] = 'pip'
  runpy.run_module('pip', run_name='__main__', alter_sys=True)
""".trimIndent()
