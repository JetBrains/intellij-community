// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerBase
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode

/**
 * Writes the build log of the incremental tests in the format of the Kotlin repository.
 *
 * JPS writes the cleaned and the compiled files, [KotlinBuilder] writes the chunks and the exit codes. The log replaces
 * [rootPath] with two spaces.
 */
internal class KotlinJpsBuildLogger(private val rootPath: String) : ProjectBuilderLoggerBase(), KotlinBuilderTestingLogger {
  private val buffer = StringBuilder()
  private val rootPrefix = Regex("^" + Regex.escape("$rootPath/"))

  val log: String
    get() = buffer.toString()

  override fun isEnabled(): Boolean = true

  override fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk) {
    if (context.projectDescriptor.project.modules.size > 1) {
      logLine("Building ${chunk.modules.sortedBy { it.name }.joinToString { it.name }}")
    }
  }

  override fun buildFinished(exitCode: ExitCode) {
    logLine("Exit code: $exitCode")
    logLine("------------------------------------------")
  }

  override fun logLine(message: String) {
    buffer.append(message.replace(rootPrefix, "  ")).append('\n')
  }
}
