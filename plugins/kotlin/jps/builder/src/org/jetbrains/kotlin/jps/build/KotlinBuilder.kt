// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.GlobalContextKey
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.StopBuildException
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.kotlin.config.CompilerRunnerConstants.KOTLIN_COMPILER_NAME
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.jps.KotlinJpsBuilderBundle
import org.jetbrains.kotlin.jps.buildtools.JpsCompilerMessageSink
import org.jetbrains.kotlin.jps.buildtools.KotlinBuildToolsLoader
import org.jetbrains.kotlin.jps.buildtools.KotlinBuildToolsSession
import org.jetbrains.kotlin.jps.model.kotlinKind
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Compiles the Kotlin sources of a module chunk through the Build Tools API.
 *
 * JPS owns the incremental compilation. The builder compiles the files JPS marked dirty and hands the outputs, the
 * symbol lookups, the inlined constants and the imports to the JPS dependency graph. It keeps no incremental caches
 * of its own. The IDE hands over the Build Tools API implementation of the project's Kotlin version, see
 * [KotlinBuildToolsLoader].
 */
class KotlinBuilder : ModuleLevelBuilder(BuilderCategory.SOURCE_PROCESSOR) {
    companion object {
        @NlsSafe
        const val KOTLIN_BUILDER_NAME: String = "Kotlin Builder"

        val LOG: Logger = Logger.getInstance("#org.jetbrains.kotlin.jps.build.KotlinBuilder")

        private val buildToolsSessionKey = GlobalContextKey<KotlinBuildToolsSession>("kotlinBuildToolsSession")

        /**
         * @return the error for the legacy modes of the build process that the builder does not support, or `null`.
         *
         * The legacy Kotlin JPS plugin only warns about such modes. This builder hands the symbol lookups only to the
         * dependency graph. Without the graph, JPS falls back to the old mappings, which drop the lookups, so an
         * incremental build would be wrong.
         */
        fun legacyModesMessageIfAny(context: CompileContext): @Nls String? {
            val legacyModes = buildList {
                if (!JavaBuilderUtil.isDepGraphEnabled() || context.projectDescriptor.dataManager.dependencyGraph == null) {
                    add("${GlobalOptions.DEPENDENCY_GRAPH_ENABLED}=false")
                }
            }
            if (legacyModes.isEmpty()) return null
            return KotlinJpsBuilderBundle.message("error.legacy.jps.modes.0", legacyModes.joinToString("; "))
        }
    }

    override fun getPresentableName(): String = KOTLIN_BUILDER_NAME

    override fun getCompilableFileExtensions(): List<String> = listOf("kt", "kts")

    override fun buildStarted(context: CompileContext) {
        context.putUserData(buildToolsSessionKey, KotlinBuildToolsSession())
        reportImplementation(context)
        // A build without Kotlin must not fail, so the error is reported only when a chunk has Kotlin to compile
        legacyModesMessageIfAny(context)?.let { LOG.warn(it) }
    }

    /**
     * Says which implementation the build uses, as a build message, so that the Build tool window shows it. The jars are
     * only listed here, not loaded: a build without Kotlin never loads the implementation.
     */
    private fun reportImplementation(context: CompileContext) {
        val home = KotlinBuildToolsLoader.implHomeProperty ?: return
        val classpath = KotlinBuildToolsLoader.resolveClasspath(home) ?: return
        LOG.debug { "Build Tools API implementation home $home holds ${classpath.joinToString { it.fileName.toString() }}" }
        val text = KotlinJpsBuilderBundle.message("info.compiling.through.build.tools.api.0.1", classpath.size, home)
        context.processMessage(CompilerMessage(KOTLIN_COMPILER_NAME, BuildMessage.Kind.JPS_INFO, text))
    }

    override fun buildFinished(context: CompileContext) {
        synchronized(kotlinCompileContextKey) {
            context.putUserData(kotlinCompileContextKey, null)
        }
        context.getUserData(buildToolsSessionKey)?.let {
            context.putUserData(buildToolsSessionKey, null)
            it.close()
        }
    }

    override fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk) {
        super.chunkBuildStarted(context, chunk)
        if (chunk.isDummy(context)) return
        val kotlinContext = ensureKotlinContextInitialized(context)
        context.testingLogger?.chunkBuildStarted(context, chunk)

        if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) return
        val kotlinChunk = kotlinContext.getChunk(chunk) ?: return
        if (kotlinChunk.isEnabled) {
            markChunkDirtyIfNeeded(context, kotlinChunk)
        }
    }

    /**
     * Some changes need a compilation of all Kotlin sources of the chunk:
     * - an argument change, as in the legacy Kotlin JPS plugin;
     * - an output that another compiler wrote after the last compilation of this builder.
     *
     * The files are marked for the first round, so JPS deletes their outputs before it. The check runs before JPS deletes
     * the outputs of the dirty sources, so the module mapping of the previous compilation is still in the output.
     */
    private fun markChunkDirtyIfNeeded(context: CompileContext, kotlinChunk: KotlinChunk) {
        val dataPaths = context.projectDescriptor.dataManager.dataPaths
        val reason = kotlinChunk.targets.firstNotNullOfOrNull { target ->
            TargetCompilerArguments(dataPaths, target.jpsModuleBuildTarget).reasonToRebuild(kotlinChunk.compilerArguments)
            ?: reasonForForeignOutput(dataPaths, target)
        } ?: return

        LOG.info("$reason. Compiling all Kotlin sources of ${kotlinChunk.presentableShortName}")
        for (target in kotlinChunk.targets) {
            FSOperations.markDirty(
                context,
                CompilationRound.NEXT,
                target.jpsModuleBuildTarget
            ) { file -> FileUtilRt.extensionEquals(file.name, "kt") }
            KotlinModuleMappingStorage.of(context, target.jpsModuleBuildTarget).clean()
        }
    }

    private fun reasonForForeignOutput(dataPaths: BuildDataPaths, target: KotlinModuleBuildTarget): String? {
        val marker = TargetCompilerMarker(dataPaths, target.jpsModuleBuildTarget)
        val lastCompilation = marker.lastWriteTime() ?: return null
        return when {
            LegacyKotlinJpsState.compiledSince(dataPaths, target.jpsModuleBuildTarget, lastCompilation) ->
                "The legacy Kotlin JPS plugin compiled $target after this builder"
            marker.isModuleMappingForeign(target.moduleMappingFile) ->
                "Another compiler rewrote ${target.moduleMappingFile} after this builder"
            else -> null
        }
    }

    override fun chunkBuildFinished(context: CompileContext, chunk: ModuleChunk) {
        super.chunkBuildFinished(context, chunk)
        if (chunk.isDummy(context)) return

        // Temporary workaround for KT-33808
        val kotlinContext = ensureKotlinContextInitialized(context)
        for (target in chunk.targets) {
            if (kotlinContext.targetsBinding[target]?.sources?.isEmpty() != false) continue
            val outputRoots = target.getOutputRoots(context)
            if (outputRoots.size > 1) {
                outputRoots.forEach { it.mkdirs() }
            }
        }
    }

    private fun ensureKotlinContextInitialized(context: CompileContext): KotlinCompileContext {
        context.getUserData(kotlinCompileContextKey)?.let { return it }

        // A chunk-local lock would not do: the context is shared by all chunks
        synchronized(kotlinCompileContextKey) {
            context.getUserData(kotlinCompileContextKey)?.let { return it }
            try {
                lateinit var kotlinContext: KotlinCompileContext
                val time = measureTimeMillis {
                    val session = context.getUserData(buildToolsSessionKey) ?: KotlinBuildToolsSession().also {
                        context.putUserData(
                            buildToolsSessionKey,
                            it
                        )
                    }
                    kotlinContext = KotlinCompileContext(context, session)
                    context.putUserData(kotlinCompileContextKey, kotlinContext)
                    kotlinContext.reportUnsupportedTargets()
                }
                LOG.info("Kotlin compile context initialized in $time ms")
                return kotlinContext
            } catch (t: Throwable) {
                jpsReportInternalBuilderError(context, Error("Cannot initialize the Kotlin context: ${t.message}", t))
                throw t
            }
        }
    }

    override fun build(
        context: CompileContext,
        chunk: ModuleChunk,
        dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
        outputConsumer: OutputConsumer,
    ): ExitCode {
        if (chunk.isDummy(context)) return ExitCode.NOTHING_DONE

        val kotlinContext = ensureKotlinContextInitialized(context)
        val representativeTarget = kotlinContext.targetsBinding[chunk.representativeTarget()] ?: return ExitCode.OK
        val sink = JpsCompilerMessageSink(context, representativeTarget)

        try {
            val exitCode = doBuild(context, chunk, kotlinContext, representativeTarget, dirtyFilesHolder, outputConsumer, sink)
            LOG.debug { "Build result of ${chunk.presentableShortName}: $exitCode" }
            context.testingLogger?.buildFinished(exitCode)
            return exitCode
        } catch (e: StopBuildException) {
            throw e
        } catch (e: BuildDataCorruptedException) {
            throw e
        } catch (e: Throwable) {
            LOG.info("Kotlin compilation of ${chunk.presentableShortName} failed", e)
            jpsReportInternalBuilderError(context, e)
            return ExitCode.ABORT
        }
    }

    private fun doBuild(
        context: CompileContext,
        chunk: ModuleChunk,
        kotlinContext: KotlinCompileContext,
        representativeTarget: KotlinModuleBuildTarget,
        dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
        outputConsumer: OutputConsumer,
        sink: JpsCompilerMessageSink,
    ): ExitCode {
        // A module imported from a source set of a multiplatform compilation is compiled as a source root of the
        // compilation module, which KotlinSourceRootProvider adds.
        if (chunk.modules.any { it.kotlinKind == KotlinModuleKind.SOURCE_SET_HOLDER }) {
            if (chunk.modules.size > 1) {
                sink.reportError(KotlinJpsBuilderBundle.message("error.cyclic.modules.in.multiplatform.project"))
                return ExitCode.ABORT
            }
            return ExitCode.NOTHING_DONE
        }

        if (!System.getProperty("kotlin.jps.enabled", "true")!!.toBoolean()) {
            sink.reportInfo(KotlinJpsBuilderBundle.message("info.kotlin.builder.is.disabled"))
            return ExitCode.NOTHING_DONE
        }

        val kotlinChunk = kotlinContext.getChunk(chunk) ?: return ExitCode.OK
        if (!kotlinChunk.haveSameCompiler) {
            sink.reportError(
                KotlinJpsBuilderBundle.message(
                    "error.cyclic.modules.0.need.the.same.compiler",
                    kotlinChunk.presentableModulesToCompilersList
                )
            )
            return ExitCode.ABORT
        }
        if (!kotlinChunk.isEnabled) {
            return ExitCode.NOTHING_DONE
        }

        val kotlinDirtyFilesHolder = KotlinDirtySourceFilesHolder(chunk, context, dirtyFilesHolder)
        if (!kotlinDirtyFilesHolder.hasDirtyOrRemovedFiles) {
            return ExitCode.NOTHING_DONE
        }

        val legacyModesMessage = legacyModesMessageIfAny(context)
        if (legacyModesMessage != null) {
            sink.reportError(legacyModesMessage)
            return ExitCode.ABORT
        }

        val targetsWithoutOutputDir = chunk.targets.filter { it.outputDir == null }
        if (targetsWithoutOutputDir.isNotEmpty()) {
            sink.reportError(KotlinJpsBuilderBundle.message("error.no.output.directory.for.0", targetsWithoutOutputDir.joinToString()))
            return ExitCode.ABORT
        }

        val session = kotlinContext.buildToolsSession.getOrCreate()
        if (session == null) {
            sink.reportError(
                KotlinJpsBuilderBundle.message(
                    "error.no.build.tools.implementation.0",
                    KotlinBuildToolsLoader.IMPL_HOME_PROPERTY
                )
            )
            return ExitCode.ABORT
        }
        val compilerVersion = session.kotlinToolchains.getCompilerVersion()

        if (needsChunkRebuild(context, kotlinChunk, kotlinDirtyFilesHolder, compilerVersion) ||
            needsChunkRebuildWithoutIncrementalCompilation(context, kotlinChunk, kotlinDirtyFilesHolder)
        ) {
            return ExitCode.CHUNK_REBUILD_REQUIRED
        }

        // The Build Tools API describes one module per compilation, so it cannot compile a cyclic chunk as one unit
        if (kotlinChunk.targets.size > 1) {
            sink.reportError(KotlinJpsBuilderBundle.message("error.cyclic.modules.0", kotlinChunk.presentableShortName))
            return ExitCode.ABORT
        }

        LOG.debug { "Compiling files: ${kotlinDirtyFilesHolder.allDirtyFiles}" }
        JavaBuilderUtil.registerFilesToCompile(context, kotlinDirtyFilesHolder.allDirtyFiles)

        val dataPaths = context.projectDescriptor.dataManager.dataPaths
        LegacyKotlinJpsState.deleteLookups(dataPaths)

        val registrar = DependencyGraphRegistrar(context, outputConsumer, kotlinContext.isInstrumentationEnabled)
        var compiledSomething = false
        for (target in kotlinChunk.targets) {
            LegacyKotlinJpsState.handOver(dataPaths, target.jpsModuleBuildTarget)
            target.nextRound(context)
            val outcome = target.compile(kotlinDirtyFilesHolder, session, context, sink) ?: continue
            compiledSomething = true
            if (!outcome.success || sink.hasErrors) break

            registrar.register(target.jpsModuleBuildTarget, outcome)
            TargetCompilerMarker(dataPaths, target.jpsModuleBuildTarget).write(compilerVersion, target.moduleMappingFile)
            TargetCompilerArguments(dataPaths, target.jpsModuleBuildTarget).write(kotlinChunk.compilerArguments)
        }

        if (!compiledSomething) {
            return ExitCode.NOTHING_DONE
        }

        if (sink.hasErrors) {
            LOG.info("Compiled with errors")
            JavaBuilderUtil.registerFilesWithErrors(context, sink.filesWithErrors.map(::File))
            return ExitCode.ABORT
        }

        JavaBuilderUtil.registerSuccessfullyCompiled(context, kotlinDirtyFilesHolder.allDirtyFiles)
        LOG.info("Compiled successfully")
        return ExitCode.OK
    }

    /**
     * With the incremental compilation off in the IDE settings, each change compiles all Kotlin sources of the chunk, as in
     * the legacy Kotlin JPS plugin. The IDE then does not pass `kotlin.incremental.compilation=true`. The setting is the
     * usual workaround for an error of the incremental compilation.
     */
    private fun needsChunkRebuildWithoutIncrementalCompilation(
        context: CompileContext,
        kotlinChunk: KotlinChunk,
        dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    ): Boolean {
        if (IncrementalCompilation.isEnabledForJvm() || JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) return false

        // JPS rebuilds a chunk only once per build, so all sources are dirty in the second pass
        val target = kotlinChunk.targets.firstOrNull { dirtyFilesHolder.getDirtyFiles(it.jpsModuleBuildTarget).size < it.sources.size }
                     ?: return false
        LOG.info("The incremental compilation is off and $target has sources that are not dirty: rebuilding ${kotlinChunk.presentableShortName}")
        return true
    }

    /**
     * The output of another compiler cannot be extended file by file: the module mapping and the class files must come
     * from one compiler. The same holds for the output of the legacy Kotlin JPS plugin, which registered the module
     * mapping with its sources. The builder asks JPS to rebuild such a chunk once.
     *
     * A target without a marker has legacy output when the module mapping or the legacy caches exist. JPS can delete the
     * module mapping with the outputs of the dirty sources, but the caches stay.
     */
    private fun needsChunkRebuild(
        context: CompileContext,
        kotlinChunk: KotlinChunk,
        dirtyFilesHolder: KotlinDirtySourceFilesHolder,
        compilerVersion: String,
    ): Boolean {
        if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) return false

        val dataPaths = context.projectDescriptor.dataManager.dataPaths
        for (target in kotlinChunk.targets) {
            val previousVersion = TargetCompilerMarker(dataPaths, target.jpsModuleBuildTarget).read()
            if (previousVersion == compilerVersion) continue

            val hasPreviousOutput = target.moduleMappingFile.isFile || LegacyKotlinJpsState.hasCompiled(dataPaths, target.jpsModuleBuildTarget)
            if (previousVersion == null && !hasPreviousOutput) continue

            if (dirtyFilesHolder.getDirtyFiles(target.jpsModuleBuildTarget).size >= target.sources.size) continue

            LOG.info("$target was compiled by Kotlin ${previousVersion ?: "<the legacy JPS plugin>"}, now $compilerVersion is used: rebuilding ${kotlinChunk.presentableShortName}")
            return true
        }
        return false
    }
}

/** The module mapping of the target, `META-INF/<module>.kotlin_module` in the output directory. */
private val KotlinModuleBuildTarget.moduleMappingFile: File
    get() = File(outputDir, "META-INF/$moduleName.${ModuleMapping.MAPPING_FILE_EXT}")
