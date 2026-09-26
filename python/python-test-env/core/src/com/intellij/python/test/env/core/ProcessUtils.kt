// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Executes a command and streams output to the logger in real-time.
 * 
 * @param command The command and arguments to execute
 * @param logger The logger to use for output
 * @param logPrefix Prefix for log messages (e.g., "pip", "miniconda installer")
 * @throws IllegalStateException if the process exits with non-zero code
 */
@ApiStatus.Internal
suspend fun executeProcess(
  command: List<String>,
  logger: Logger,
  logPrefix: String = "process"
) = withContext(Dispatchers.IO) {
  val processBuilder = ProcessBuilder(command)
  processBuilder.redirectErrorStream(true)
  
  logger.info("Running command: ${command.joinToString(" ")}")
  val process = processBuilder.start()
  
  // Stream output line by line in real-time for debugging stuck processes
  process.inputStream.bufferedReader().use { reader ->
    reader.lineSequence().forEach { line ->
      logger.info("$logPrefix: $line")
    }
  }

  val exitCode = process.awaitExit()
  if (exitCode != 0) {
    logger.error("Process failed with exit code $exitCode")
    error("Process execution failed. Exit code: $exitCode")
  }
  else {
    logger.info("Process completed successfully")
  }
}

