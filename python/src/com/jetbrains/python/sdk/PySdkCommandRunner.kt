// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.errorProcessing.PyExecResult
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Executes a given executable with specified arguments within an optional project directory.
 *
 * @param [executable] The [Path] to the executable to run.
 * @param [projectPath] The path to the project directory in which to run the executable, or null if no specific directory is required.
 * @param [args] The arguments to pass to the executable.
 * @return A [Result] object containing the output of the command execution.
 */
@Internal
suspend fun runExecutable(executable: Path, projectPath: Path?, vararg args: String): PyExecResult<String> =
  ExecService().execGetStdout(WhatToExec.Binary(executable), args.toList(), ExecOptions(workingDirectory = projectPath))