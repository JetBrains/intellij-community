// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.hatch.runtime.HatchRuntime
import com.intellij.util.Url
import com.intellij.util.Urls
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError

/**
 * Manage environment dependencies
 */
class HatchSelf(runtime: HatchRuntime) : HatchCommand("self", runtime) {

  /**
   * Generate a pre-populated GitHub issue.
   */
  suspend fun report(): Result<Url, ExecError> {
    return executeAndHandleErrors("report", "--no-open") { processOutput ->
      val output = processOutput.takeIf { it.exitCode == 0 }?.stdout?.trim()
                   ?: return@executeAndHandleErrors Result.failure(null)
      val url = Urls.parseEncoded(output)
      if (url != null) {
        Result.success(url)
      }
      else {
        Result.failure(PyHatchBundle.message("python.hatch.cli.error.response.out.of.pattern", "URL"))
      }
    }
  }

  /**
   * Restore the installation
   */
  fun restore(): Result<String, ExecError> = TODO()

  /**
   * Install the latest version
   */
  fun update(): Result<String, ExecError> = TODO()
}