// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.targets

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ProjectBuildException
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.jps.KotlinJpsBuilderBundle
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.jps.build.KotlinChunk
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder
import org.jetbrains.kotlin.jps.build.KotlinIncludedModuleSourceRoot
import org.jetbrains.kotlin.jps.build.isKotlinSourceFile
import org.jetbrains.kotlin.jps.build.productionBuildTarget
import org.jetbrains.kotlin.jps.buildtools.JpsCompilerMessageSink
import org.jetbrains.kotlin.jps.buildtools.JvmCompilationOutcome
import org.jetbrains.kotlin.jps.model.productionOutputFilePath
import org.jetbrains.kotlin.jps.model.testOutputFilePath
import java.io.File

/** The Kotlin view of one production or test module build target. */
abstract class KotlinModuleBuildTarget internal constructor(
  val kotlinContext: KotlinCompileContext,
  val jpsModuleBuildTarget: ModuleBuildTarget,
) {
  /**
   * The context of the whole build. Round dependent data, such as the temporary source roots with Groovy stubs, is
   * visible only in the context of the round.
   */
  val jpsGlobalContext: CompileContext
    get() = kotlinContext.jpsContext

  // Initialized in KotlinTargetsIndexBuilder
  lateinit var chunk: KotlinChunk

  open fun isEnabled(chunkCompilerArguments: Lazy<CommonCompilerArguments>): Boolean = true

  val module: JpsModule
    get() = jpsModuleBuildTarget.module

  val isTests: Boolean
    get() = jpsModuleBuildTarget.isTests

  /**
   * The name the compiler gets as `-module-name`.
   *
   * A Gradle source set is imported as a separate module, so `X.main` and `X.test` are two JPS modules. The compiler
   * mangles the names of internal members with the module name, so the test module takes the name of its production
   * module (KT-11993).
   */
  open val moduleName: String
    get() = relatedProductionModule?.name ?: jpsModuleBuildTarget.id

  val outputDir: File by lazy {
    val explicitOutputPath = if (isTests) module.testOutputFilePath else module.productionOutputFilePath
    explicitOutputPath?.let { File(it).absoluteFile.parentFile }
      ?: jpsModuleBuildTarget.outputDir
      ?: throw ProjectBuildException(KotlinJpsBuilderBundle.message("error.no.output.directory.for.0", this))
  }

  val friendBuildTargets: List<KotlinModuleBuildTarget>
    get() {
      if (!isTests) return emptyList()
      return listOfNotNull(
        kotlinContext.targetsBinding[module.productionBuildTarget],
        relatedProductionModule?.let { kotlinContext.targetsBinding[it.productionBuildTarget] },
      ).filter { it.sources.isNotEmpty() }
    }

  val friendOutputDirs: List<File>
    get() = friendBuildTargets.mapNotNull { JpsJavaExtensionService.getInstance().getOutputDirectory(it.module, false) }

  private val relatedProductionModule: JpsModule?
    get() = JpsJavaExtensionService.getInstance().getTestModuleProperties(module)?.productionModule

  val allDependencies by lazy {
    JpsJavaExtensionService.dependencies(module).recursively().exportedOnly().includedIn(JpsJavaClasspathKind.compile(isTests))
  }

  /**
   * All sources of this target, dirty or not.
   *
   * The value is computed on demand and reset on each round, because a round can add source roots.
   */
  val sources: Map<File, Source>
    get() = lazySources.value

  @Volatile
  private var lazySources: Lazy<Map<File, Source>> = lazy { computeSources(jpsGlobalContext) }

  fun nextRound(localContext: CompileContext) {
    lazySources = lazy { computeSources(localContext) }
  }

  private fun computeSources(localContext: CompileContext): Map<File, Source> {
    val result = LinkedHashMap<File, Source>()
    val moduleExcludes = module.excludeRootsList.urls.mapTo(HashSet(), JpsPathUtil::urlToFile)
    val compilerExcludes = JpsJavaExtensionService.getInstance().getCompilerConfiguration(module.project).compilerExcludes

    val roots = localContext.projectDescriptor.buildRootIndex.getTargetRoots(jpsModuleBuildTarget, localContext)
    for (rootDescriptor in roots) {
      val isCrossCompiled = rootDescriptor is KotlinIncludedModuleSourceRoot
      rootDescriptor.root.walkTopDown()
        .onEnter { it !in moduleExcludes }
        .forEach { file ->
          if (!compilerExcludes.isExcluded(file) && file.isFile && file.isKotlinSourceFile) {
            result[file] = Source(file, isCrossCompiled)
          }
        }
    }
    return result
  }

  /**
   * @property isCrossCompiled the source is compiled for several targets
   */
  class Source(val file: File, val isCrossCompiled: Boolean)

  fun isFromIncludedSourceRoot(file: File): Boolean = sources[file]?.isCrossCompiled == true

  override fun toString(): String = jpsModuleBuildTarget.toString()

  /**
   * Compiles the dirty sources of this target.
   *
   * @return `null` when the round has nothing to compile for this target
   */
  abstract fun compile(
    dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    session: KotlinToolchains.BuildSession,
    context: CompileContext,
    sink: JpsCompilerMessageSink,
  ): JvmCompilationOutcome?

  protected fun collectSourcesToCompile(dirtyFilesHolder: KotlinDirtySourceFilesHolder): SourcesToCompile = SourcesToCompile(
    sources = dirtyFilesHolder.getDirtyFiles(jpsModuleBuildTarget).values,
    removedFiles = dirtyFilesHolder.getRemovedFiles(jpsModuleBuildTarget),
  )

  inner class SourcesToCompile(sources: Collection<Source>, val removedFiles: Collection<File>) {
    val allFiles: List<File> = sources.map { it.file }
    val crossCompiledFiles: List<File> = sources.filter { it.isCrossCompiled }.map { it.file }

    /**
     * @return `true` when there are files to compile or removed files
     */
    fun logFiles(): Boolean {
      val hasDirtyOrRemovedSources = allFiles.isNotEmpty() || removedFiles.isNotEmpty()
      if (hasDirtyOrRemovedSources) {
        val logger = jpsGlobalContext.loggingManager.projectBuilderLogger
        if (logger.isEnabled) {
          logger.logCompiledFiles(allFiles, KotlinBuilder.KOTLIN_BUILDER_NAME, "Compiling files:")
        }
      }
      return hasDirtyOrRemovedSources
    }
  }
}
