// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.GlobalContextKey
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.kotlin.config.CompilerRunnerConstants.KOTLIN_COMPILER_NAME
import org.jetbrains.kotlin.jps.KotlinJpsBuilderBundle
import org.jetbrains.kotlin.jps.buildtools.KotlinBuildToolsSession
import org.jetbrains.kotlin.jps.targets.KotlinTargetsIndex
import org.jetbrains.kotlin.jps.targets.KotlinTargetsIndexBuilder
import org.jetbrains.kotlin.jps.targets.KotlinUnsupportedModuleBuildTarget

/**
 * The Kotlin state of one build, shared between all threads. It is created before the first Kotlin chunk and dropped
 * when the build finishes.
 */
internal val CompileContext.kotlin: KotlinCompileContext
  get() = getUserData(kotlinCompileContextKey)
          ?: error("KotlinCompileContext is available only between the first KotlinBuilder.chunkBuildStarted and KotlinBuilder.buildFinished")

internal val kotlinCompileContextKey = GlobalContextKey<KotlinCompileContext>("kotlin")

class KotlinCompileContext(val jpsContext: CompileContext, val buildToolsSession: KotlinBuildToolsSession) {
  val targetsIndex: KotlinTargetsIndex = KotlinTargetsIndexBuilder(this).build()

  val targetsBinding
    get() = targetsIndex.byJpsTarget

  val isInstrumentationEnabled: Boolean by lazy {
    val value = System.getProperty("kotlin.jps.instrument.bytecode")?.toBoolean() ?: false
    if (value) {
      val message = KotlinJpsBuilderBundle.message("info.experimental.bytecode.instrumentation.is.enabled")
      jpsContext.processMessage(CompilerMessage(KOTLIN_COMPILER_NAME, BuildMessage.Kind.INFO, message))
    }
    value
  }

  fun getChunk(rawChunk: ModuleChunk): KotlinChunk? {
    val rawRepresentativeTarget = rawChunk.representativeTarget()
    if (rawRepresentativeTarget !in targetsBinding) return null
    return targetsIndex.chunksByJpsRepresentativeTarget[rawRepresentativeTarget]
           ?: error("The Kotlin chunk of $rawChunk was not loaded at the build start")
  }

  /** Warns once per build about the targets of a Kotlin platform that the IDE build system does not compile. */
  fun reportUnsupportedTargets() {
    val byKind = LinkedHashMap<String?, MutableList<KotlinUnsupportedModuleBuildTarget>>()
    for (chunk in targetsIndex.chunks) {
      val target = chunk.representativeTarget
      if (target is KotlinUnsupportedModuleBuildTarget && target.sources.isNotEmpty()) {
        byKind.getOrPut(target.kind) { ArrayList() }.add(target)
      }
    }

    for ((kind, targets) in byKind) {
      targets.sortBy { it.module.name }
      val chunkNames = targets.joinToString { it.chunk.presentableShortName }
      val message = if (kind == null) {
        KotlinJpsBuilderBundle.message("warning.platform.is.not.supported.for.0", chunkNames)
      }
      else {
        KotlinJpsBuilderBundle.message("warning.platform.0.is.not.supported.for.1", kind, chunkNames)
      }
      jpsContext.processMessage(CompilerMessage(KOTLIN_COMPILER_NAME, BuildMessage.Kind.WARNING, message))
    }
  }
}
