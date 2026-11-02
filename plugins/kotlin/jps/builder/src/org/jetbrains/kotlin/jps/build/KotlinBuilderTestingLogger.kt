// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode

/**
 * Receives the rounds of [KotlinBuilder] in tests.
 *
 * The builder reports to the project builder logger of the build when that logger implements this interface. A
 * production build has no such logger, so the builder reports nothing there.
 */
interface KotlinBuilderTestingLogger {
  /** The builder starts a chunk that is not dummy. */
  fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk)

  /** One call of [ModuleLevelBuilder.build] for a chunk that is not dummy returns [exitCode]. */
  fun buildFinished(exitCode: ExitCode)
}

internal val CompileContext.testingLogger: KotlinBuilderTestingLogger?
  get() = loggingManager.projectBuilderLogger as? KotlinBuilderTestingLogger
