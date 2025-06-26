// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.CliResult
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.TelemetryManager

internal class StopTelemetryStarter : ApplicationStarter {
  override fun canProcessExternalCommandLine(): Boolean = true

  override fun main(args: List<String>) {
    logger<StopTelemetryStarter>().info("IDE is not running")
    println("IDE is not running")
  }

  override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
    logger<StopTelemetryStarter>().info("Stopping the telemetry...")
    try {
      TelemetryManager.Companion.getInstance().shutdownExporters()
      return CliResult(0, "Telemetry was stopped") // NON-NLS
    }
    catch (ex: Throwable) {
      logger<StopTelemetryStarter>().error(ex)
      return CliResult(1, ex.message ?: "Unknown error")
    }
  }
}