// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildTargetRegistry
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.JpsBuildBundle
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.kotlin.config.SettingConstants

/**
 * Tells the IDE which modules a build compiled completely, so that the Kotlin compiler reference index can trust their output.
 *
 * Based on [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder].
 * TODO: Currently, disabled: KTIJ-40515
 */
class KotlinCompilerReferenceIndexBuilder : ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {
  private val compiledTargets = ContainerUtil.newConcurrentSet<ModuleBuildTarget>()

  override fun getPresentableName(): String = JpsBuildBundle.message("builder.name.backward.references.indexer")

  override fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer,
  ): ExitCode {
    chunk.targets.filterTo(compiledTargets) { context.scope.isWholeTargetAffected(it) }
    return ExitCode.OK
  }

  override fun buildFinished(context: CompileContext) {
    val targetIndex = context.projectDescriptor.buildTargetIndex
    for (module in context.projectDescriptor.project.modules) {
      val allAreDummyOrCompiled = targetIndex.getModuleBasedTargets(module, BuildTargetRegistry.ModuleTargetSelector.ALL)
        .none { target -> target is ModuleBuildTarget && target !in compiledTargets && !targetIndex.isDummy(target) }

      if (allAreDummyOrCompiled) {
        context.processMessage(
          CustomBuilderMessage(SettingConstants.KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID, MESSAGE_TYPE, module.name)
        )
      }
    }

    compiledTargets.clear()
  }

  override fun getCompilableFileExtensions(): List<String> = emptyList()

  private companion object {
    const val MESSAGE_TYPE = "processed module"
  }
}
