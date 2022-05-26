package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDERunContext
import java.nio.file.Path
import kotlin.time.Duration

data class IDEStartResult(
  val runContext: IDERunContext,
  val logsDir: Path,
  val executionTime: Duration,
  val vmOptionsDiff: VMOptionsDiff? = null
) {
  val context: IDETestContext get() = runContext.testContext

  val extraAttributes: MutableMap<String, String> = mutableMapOf()
}