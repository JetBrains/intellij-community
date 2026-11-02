// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.model.module.JpsModule

fun ModuleChunk.isDummy(context: CompileContext): Boolean {
  val targetIndex = context.projectDescriptor.buildTargetIndex
  return targets.all { targetIndex.isDummy(it) }
}

fun ModuleBuildTarget(module: JpsModule, isTests: Boolean): ModuleBuildTarget =
  ModuleBuildTarget(module, if (isTests) JavaModuleBuildTargetType.TEST else JavaModuleBuildTargetType.PRODUCTION)

val JpsModule.productionBuildTarget: ModuleBuildTarget
  get() = ModuleBuildTarget(this, false)

fun jpsReportInternalBuilderError(context: CompileContext, error: Throwable) {
  @Suppress("HardCodedStringLiteral")
  context.processMessage(CompilerMessage.createInternalBuilderError("Kotlin", error))
}
