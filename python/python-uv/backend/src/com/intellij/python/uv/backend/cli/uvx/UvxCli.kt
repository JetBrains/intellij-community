// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uvx

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.pytools.runtime.executeAndHandleErrors
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Run a command provided by a Python package (alias for `uv tool run`).
 */
@Suppress("unused")
class UvxCli(private val runtime: PyToolRuntime) {
  /**
   * Run [command] with the given [arguments].
   *
   * @param from Use the given package to provide the command.
   * @param with Run with the given packages installed.
   * @param withEditable Run with the given packages installed in editable mode.
   * @param withRequirements Run with the packages listed in the given requirements files.
   * @param isolated Run the tool in an isolated virtual environment, ignoring any already-installed tools.
   * @param python Use a specific Python interpreter.
   */
  suspend fun run(
    command: String,
    vararg arguments: String,
    from: String? = null,
    with: List<String> = emptyList(),
    withEditable: List<String> = emptyList(),
    withRequirements: List<String> = emptyList(),
    isolated: Boolean? = null,
    python: String? = null,
  ): PyResult<String> {
    val options = buildList {
      from?.let { add("--from"); add(it) }
      with.forEach { add("--with"); add(it) }
      withEditable.forEach { add("--with-editable"); add(it) }
      withRequirements.forEach { add("--with-requirements"); add(it) }
      if (isolated == true) add("--isolated")
      python?.let { add("--python"); add(it) }
    }.toTypedArray()

    return runtime.executeAndHandleErrors(*options, command, *arguments, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Display the uvx version.
   */
  suspend fun getVersion(): PyResult<String> {
    return runtime.executeAndHandleErrors("--version", transformer = ZeroCodeStdoutTransformer)
  }
}
