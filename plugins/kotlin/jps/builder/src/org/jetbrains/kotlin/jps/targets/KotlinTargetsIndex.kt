// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.targets

import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.jps.build.KotlinChunk
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.model.platform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.isJvm
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import kotlin.system.measureTimeMillis

/** The Kotlin view of every module build target of the project. */
class KotlinTargetsIndex(
  val byJpsTarget: Map<ModuleBuildTarget, KotlinModuleBuildTarget>,
  val chunks: List<KotlinChunk>,
  val chunksByJpsRepresentativeTarget: Map<ModuleBuildTarget, KotlinChunk>,
)

internal class KotlinTargetsIndexBuilder(private val uninitializedContext: KotlinCompileContext) {
  private val byJpsModuleBuildTarget = LinkedHashMap<ModuleBuildTarget, KotlinModuleBuildTarget>()
  private val chunks = ArrayList<KotlinChunk>()

  fun build(): KotlinTargetsIndex {
    val time = measureTimeMillis {
      val jpsContext = uninitializedContext.jpsContext

      for (chunk in jpsContext.projectDescriptor.buildTargetIndex.getSortedTargetChunks(jpsContext)) {
        val moduleBuildTargets = chunk.targets.filterIsInstance<ModuleBuildTarget>().map(::ensureLoaded)
        if (moduleBuildTargets.isNotEmpty()) {
          val kotlinChunk = KotlinChunk(uninitializedContext, moduleBuildTargets)
          moduleBuildTargets.forEach { it.chunk = kotlinChunk }
          chunks.add(kotlinChunk)
        }
      }
    }

    KotlinBuilder.LOG.info("KotlinTargetsIndex created in $time ms")

    return KotlinTargetsIndex(
      byJpsModuleBuildTarget,
      chunks,
      chunks.associateBy { it.representativeTarget.jpsModuleBuildTarget },
    )
  }

  private fun ensureLoaded(target: ModuleBuildTarget): KotlinModuleBuildTarget = byJpsModuleBuildTarget.computeIfAbsent(target) {
    val platform = target.module.platform?.idePlatformKind ?: JvmPlatforms.defaultJvmPlatform.idePlatformKind
    when {
      platform.isJvm -> KotlinJvmModuleBuildTarget(uninitializedContext, target)
      else -> KotlinUnsupportedModuleBuildTarget(uninitializedContext, target)
    }
  }
}
