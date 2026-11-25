// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.python.community.execService.ZeroCodeStdoutParserTransformer
import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.util.Url
import com.intellij.util.Urls
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.PathHolder

/**
 * Manage environment dependencies
 */
class HatchSelf<P : PathHolder>(runtime: PyToolRuntime) : HatchCommand<P>("self", runtime) {

  /**
   * Generate a pre-populated GitHub issue.
   */
  suspend fun report(): PyResult<Url> {
    return executeAndHandleErrors("report", "--no-open", transformer = ZeroCodeStdoutParserTransformer { output ->
      val url = Urls.parseEncoded(output)
      if (url != null) {
        Result.success(url)
      }
      else {
        Result.failure(PyHatchBundle.message("python.hatch.cli.error.response.out.of.pattern", "URL"))
      }
    })
  }

  /**
   * Restore the installation
   */
  fun restore(): PyResult<String> = TODO()

  /**
   * Install the latest version
   */
  fun update(): PyResult<String> = TODO()
}