// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.CliResult
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import kotlin.system.exitProcess

internal class StopTelemetryStarter : ModernApplicationStarter() {
  override fun canProcessExternalCommandLine(): Boolean = true

  override suspend fun start(args: List<String>) {
    logger<StopTelemetryStarter>().info("IDE is not running")
    println("IDE is not running")

    exitProcess(0)
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