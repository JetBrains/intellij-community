// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.targets

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder
import org.jetbrains.kotlin.jps.buildtools.JpsCompilerMessageSink
import org.jetbrains.kotlin.jps.buildtools.JvmCompilationOutcome
import org.jetbrains.kotlin.jps.model.platform
import org.jetbrains.kotlin.platform.idePlatformKind

/** A target of a Kotlin platform that the IDE build system does not compile. The build reports it once and skips it. */
class KotlinUnsupportedModuleBuildTarget(
  kotlinContext: KotlinCompileContext,
  jpsModuleBuildTarget: ModuleBuildTarget,
) : KotlinModuleBuildTarget(kotlinContext, jpsModuleBuildTarget) {
  val kind: String? = module.platform?.idePlatformKind?.name

  override fun isEnabled(chunkCompilerArguments: Lazy<CommonCompilerArguments>): Boolean = false

  override fun compile(
    dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    session: KotlinToolchains.BuildSession,
    context: CompileContext,
    sink: JpsCompilerMessageSink,
  ): JvmCompilationOutcome? = error("An unsupported target must not be compiled: $this")
}
