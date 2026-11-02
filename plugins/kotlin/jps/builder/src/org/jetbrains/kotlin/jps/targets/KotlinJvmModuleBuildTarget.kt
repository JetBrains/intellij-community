// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.targets

import com.intellij.openapi.diagnostic.debug
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsSdkDependency
import org.jetbrains.jps.service.JpsServiceManager
import org.jetbrains.kotlin.build.JvmSourceRoot
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder
import org.jetbrains.kotlin.jps.build.KotlinModuleMappingStorage
import org.jetbrains.kotlin.jps.buildtools.JpsCompilerMessageSink
import org.jetbrains.kotlin.jps.buildtools.JvmCompilationOutcome
import org.jetbrains.kotlin.jps.buildtools.JvmCompilationRequest
import org.jetbrains.kotlin.jps.buildtools.JvmCompilationRunner
import org.jetbrains.kotlin.jps.model.k2JvmCompilerArguments
import org.jetbrains.kotlin.jps.model.kotlinCompilerArguments
import org.jetbrains.kotlin.jps.model.kotlinCompilerSettings
import java.io.File
import java.nio.file.Files

/** A Kotlin/JVM target. The only kind of target the builder compiles. */
class KotlinJvmModuleBuildTarget(kotlinContext: KotlinCompileContext, jpsModuleBuildTarget: ModuleBuildTarget) :
  KotlinModuleBuildTarget(kotlinContext, jpsModuleBuildTarget) {

  override val moduleName: String
    get() = module.k2JvmCompilerArguments.moduleName ?: super.moduleName

  override fun compile(
    dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    session: KotlinToolchains.BuildSession,
    context: CompileContext,
    sink: JpsCompilerMessageSink,
  ): JvmCompilationOutcome? {
    val sources = collectSourcesToCompile(dirtyFilesHolder)
    if (!sources.logFiles()) {
      KotlinBuilder.LOG.debug { "Not compiling $this: no files affected" }
      return null
    }

    if (KotlinBuilder.LOG.isDebugEnabled) {
      val removed = sources.removedFiles.size
      KotlinBuilder.LOG.debug("Compiling ${sources.allFiles.size} files" + (if (removed == 0) "" else " ($removed removed files)") + " of $this")
    }

    val request = JvmCompilationRequest(
      moduleName = moduleName,
      sources = preprocessSources(sources.allFiles),
      commonSources = preprocessSources(sources.crossCompiledFiles),
      outputDirectory = outputDir,
      // The output directory stays on the classpath: it holds the classes of the sources that are not compiled again
      classpathRoots = findClassPathRoots(),
      javaSourceRoots = findJavaSourceRoots(context),
      friendDirectories = friendOutputDirs,
      modularJdkRoot = findModularJdkRoot(),
      commonArguments = module.kotlinCompilerArguments,
      jvmArguments = module.k2JvmCompilerArguments,
      compilerSettings = module.kotlinCompilerSettings,
      previousModuleMapping = KotlinModuleMappingStorage.of(context, jpsModuleBuildTarget).read(),
    )

    return JvmCompilationRunner(session, context).compile(request, sink)
  }

  /**
   * Internal API for source level code preprocessors.
   *
   * Used by https://plugins.jetbrains.com/plugin/13355-spot-profiler-for-java
   */
  interface SourcesPreprocessor {
    /**
     * Preprocesses some sources and returns the paths of the resulting files.
     * This function must be pure and return the same output for the same input, because JPS maps the outputs to the sources.
     */
    fun preprocessSources(srcFiles: List<File>): List<File>
  }

  fun preprocessSources(srcFiles: List<File>): List<File> {
    var result = srcFiles
    for (preprocessor in JpsServiceManager.getInstance().getExtensions(SourcesPreprocessor::class.java)) {
      result = preprocessor.preprocessSources(result)
    }
    return result
  }

  private fun findClassPathRoots(): List<File> = allDependencies.classes().roots.filter { file ->
    val path = file.toPath()
    if (Files.notExists(path)) {
      val extension = path.fileName?.toString()?.substringAfterLast('.', "") ?: ""
      // Keep a missing jar or class file: the compiler reports the absence
      extension == "class" || extension == "jar"
    }
    else {
      true
    }
  }

  private fun findModularJdkRoot(): File? {
    // The JRE modules of a modular JDK look like `jrt:///path/to/jdk!/java.base`
    val jrtPrefix = URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR
    val url = JpsJavaExtensionService.dependencies(module)
      .satisfying { it is JpsSdkDependency }
      .classes().urls
      .firstOrNull { it.startsWith(jrtPrefix) } ?: return null
    return File(url.substringAfter(jrtPrefix).substringBeforeLast(URLUtil.JAR_SEPARATOR))
  }

  private fun findJavaSourceRoots(context: CompileContext): List<JvmSourceRoot> {
    val roots = context.projectDescriptor.buildRootIndex.getTargetRoots(jpsModuleBuildTarget, context)
    return roots.mapNotNull { root ->
      val path = root.rootFile
      val file = path.toFile()
      if (Files.exists(path) && (Files.isDirectory(path) || file.extension == "java")) {
        JvmSourceRoot(file, root.packagePrefix.ifEmpty { null })
      }
      else {
        null
      }
    }
  }
}
