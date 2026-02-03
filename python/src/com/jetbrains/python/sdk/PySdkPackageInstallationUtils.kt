// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.executeHelper
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Installs an executable via a Python helper.
 *
 * @param [pythonExecutable] The path to the Python executable (could be "py" or "python").
 *
 * @return executable [Path]
 */
@Internal
suspend fun installExecutableViaPythonScript(pythonExecutable: BinaryToExec, vararg args: String): PyResult<Path> {
  val output = ExecService().executeHelper(pythonExecutable, "pycharm_package_installer.py", args.toList()).getOr { return it }
  return Result.success(Path.of(output.split("\n").last()))
}