// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.kotlin.buildtools.api.BaseCompilationOperation
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import org.jetbrains.kotlin.buildtools.api.ExecutionPolicy
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.OperationCancelledException
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments
import org.jetbrains.kotlin.buildtools.api.compilerRefIndexConfiguration
import org.jetbrains.kotlin.buildtools.api.getToolchain
import org.jetbrains.kotlin.buildtools.api.jps.jvm.JvmJpsManagedIncrementalCompilationConfiguration
import org.jetbrains.kotlin.buildtools.api.jps.jvm.operations.jpsManagedIcConfigurationBuilder
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation
import org.jetbrains.kotlin.build.JvmSourceRoot
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import org.jetbrains.kotlin.compilerRunner.mergeBeans
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.additionalArgumentsAsList
import org.jetbrains.kotlin.jps.KotlinJpsBuilderBundle
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One Kotlin/JVM compilation of one target: the sources of this round and the roots the compiler needs.
 *
 * The Build Tools API has no `-Xbuild-file`, so the target is described through ordinary compiler arguments.
 */
class JvmCompilationRequest(
  val moduleName: String,
  val sources: List<File>,
  val commonSources: List<File>,
  val outputDirectory: File,
  val classpathRoots: List<File>,
  val javaSourceRoots: List<JvmSourceRoot>,
  val friendDirectories: List<File>,
  val modularJdkRoot: File?,
  val compilerRefIndexDirectory: Path,
  val commonArguments: CommonCompilerArguments,
  val jvmArguments: K2JVMCompilerArguments,
  val compilerSettings: CompilerSettings,
  /** The module mapping of the last successful compilation, or `null` when there is none. */
  val previousModuleMapping: ByteArray?,
)

/** What one compilation produced. */
class JvmCompilationOutcome(
  val request: JvmCompilationRequest,
  val trackers: JvmCompilationTrackers,
  val success: Boolean,
)

/**
 * Compiles one target through the Build Tools API, with the execution policy of [KotlinBuildToolsSession]: the daemon
 * policy while the `kotlin.daemon.enabled` system property is set, an in-process policy otherwise.
 *
 * When the daemon compilation ends with an internal error, the compilation runs once more in process. The API also
 * returns an internal error when it cannot connect to the daemon. An exception of the daemon compilation fails the
 * compilation without a retry.
 */
class JvmCompilationRunner(
  private val session: KotlinToolchains.BuildSession,
  private val buildToolsSession: KotlinBuildToolsSession,
  private val context: CompileContext,
) {
  fun compile(request: JvmCompilationRequest, sink: JpsCompilerMessageSink): JvmCompilationOutcome {
    val policy = buildToolsSession.executionPolicy()
    if (policy !is ExecutionPolicy.WithDaemon) {
      val (trackers, result) = compileOnce(request, sink, policy)
      return JvmCompilationOutcome(request, trackers, result == CompilationResult.COMPILATION_SUCCESS)
    }

    // An error message of the attempt fails the whole JPS build, so the attempt holds the errors back
    val attemptSink = DaemonAttemptMessageSink(sink)
    try {
      val (trackers, result) = compileOnce(request, attemptSink, policy)
      if (result == CompilationResult.COMPILER_INTERNAL_ERROR) {
        return fallBackToInProcess(request, sink, attemptSink)
      }
      return JvmCompilationOutcome(request, trackers, result == CompilationResult.COMPILATION_SUCCESS)
    }
    finally {
      // The fallback has dropped the held errors already. In each other case, the held errors go to JPS.
      attemptSink.flush()
    }
  }

  /**
   * Runs one compilation again in process and switches the whole build to the in-process policy.
   */
  private fun fallBackToInProcess(
    request: JvmCompilationRequest,
    sink: JpsCompilerMessageSink,
    attemptSink: DaemonAttemptMessageSink,
  ): JvmCompilationOutcome {
    KotlinBuilder.LOG.info("The Kotlin daemon returned an internal error for ${request.moduleName}. Compiling it in process")
    attemptSink.discard()
    sink.reportWarning(KotlinJpsBuilderBundle.message("warning.kotlin.daemon.failed.0", request.moduleName))
    val (trackers, result) = compileOnce(request, sink, buildToolsSession.fallBackToInProcess())
    return JvmCompilationOutcome(request, trackers, result == CompilationResult.COMPILATION_SUCCESS)
  }

  /**
   * Runs one compilation through the Build Tools API.
   *
   * @return the trackers of the compilation and its result.
   */
  private fun compileOnce(
    request: JvmCompilationRequest,
    sink: CompilationMessageSink,
    policy: ExecutionPolicy,
  ): Pair<JvmCompilationTrackers, CompilationResult> {
    val trackers = JvmCompilationTrackers()
    try {
      val operation = buildOperation(request, trackers, sink)
      val result = withCancellationWatchdog(operation) {
        session.executeOperation(operation, policy, sink)
      }
      // The diagnostics reached JPS through the renderer. An internal compiler error can come back without an
      // error message, so report the result explicitly.
      if (result != CompilationResult.COMPILATION_SUCCESS && !sink.hasErrors) {
        sink.reportError(KotlinJpsBuilderBundle.message("error.compilation.of.0.failed.1", request.moduleName, result))
      }
      return trackers to result
    }
    catch (e: OperationCancelledException) {
      KotlinBuilder.LOG.info("Kotlin compilation of ${request.moduleName} was cancelled", e)
      // Let JPS stop the build. It throws when the build is really cancelled, and the operation cannot cancel itself.
      context.checkCanceled()
      throw e
    }
  }

  private fun buildOperation(request: JvmCompilationRequest, trackers: JvmCompilationTrackers, sink: CompilationMessageSink): JvmCompilationOperation {
    val jvm = session.kotlinToolchains.getToolchain<JvmPlatformToolchain>()
    val builder = jvm.jvmCompilationOperationBuilder(request.sources.map { it.toPath() }, request.outputDirectory.toPath())

    applyCompilerArguments(builder, request)

    val cache = OutputDirectoryIncrementalCache(request.outputDirectory, request.moduleName, request.previousModuleMapping)
    val icBuilder = builder.jpsManagedIcConfigurationBuilder(SingleTargetIncrementalCompilationComponents(cache))
    icBuilder[JvmJpsManagedIncrementalCompilationConfiguration.LOOKUP_TRACKER] = trackers.lookups
    icBuilder[JvmJpsManagedIncrementalCompilationConfiguration.INLINE_CONST_TRACKER] = trackers.inlineConstants
    icBuilder[JvmJpsManagedIncrementalCompilationConfiguration.ENUM_WHEN_TRACKER] = trackers.enumWhens
    icBuilder[JvmJpsManagedIncrementalCompilationConfiguration.IMPORT_TRACKER] = trackers.imports
    icBuilder[JvmJpsManagedIncrementalCompilationConfiguration.FILE_MAPPING_TRACKER] = trackers.outputs
    builder[JvmCompilationOperation.INCREMENTAL_COMPILATION] = icBuilder.build()

    builder[BaseCompilationOperation.COMPILER_MESSAGE_RENDERER] = sink
    attachCompilerRefIndex(builder, request)

    return builder.build()
  }

  /**
   * Attaches the compiler reference index configuration, or skips the index when the loaded implementation does not
   * support it.
   *
   * A pre-2.5.20 implementation rejects the configuration with [IllegalStateException] from the API wrapper. A
   * 2.5.20-dev implementation from before the option existed lacks the builder method, which surfaces as
   * [LinkageError]. The first rejection logs one warning, and the loader remembers it, so later compilations skip
   * the index without another warning.
   */
  private fun attachCompilerRefIndex(builder: JvmCompilationOperation.Builder, request: JvmCompilationRequest) {
    if (KotlinBuildToolsLoader.isCompilerRefIndexUnsupported()) return

    try {
      builder[BaseCompilationOperation.COMPILER_REF_INDEX] = builder.compilerRefIndexConfiguration(request.compilerRefIndexDirectory)
    }
    catch (e: IllegalStateException) {
      skipCompilerRefIndex(e)
    }
    catch (e: LinkageError) {
      skipCompilerRefIndex(e)
    }
  }

  private fun skipCompilerRefIndex(rejection: Throwable) {
    if (!KotlinBuildToolsLoader.claimCompilerRefIndexUnsupported()) return
    KotlinBuilder.LOG.warn(KotlinJpsBuilderBundle.message("warning.no.compiler.ref.index.0", rejection.message ?: rejection.toString()))
  }

  private fun applyCompilerArguments(builder: JvmCompilationOperation.Builder, request: JvmCompilationRequest) {
    val arguments = mergeBeans(request.commonArguments, XmlSerializerUtil.createCopy(request.jvmArguments))

    // The Build Tools API restricts these arguments. `-d` comes from the operation builder, the others cannot apply to
    // a JPS build. Passing one of them aborts the operation.
    arguments.destination = null
    arguments.buildFile = null
    arguments.expression = null
    arguments.includeRuntime = false
    arguments.incrementalCompilation = null

    // JPS puts the standard library and kotlin-reflect on the classpath itself
    arguments.noStdlib = true
    arguments.noReflect = true

    // A modular JDK reaches the compiler as `-jdk-home`. For an older JDK the SDK jars are on the classpath, which is
    // the `-no-jdk` case. `-no-jdk` with `-jdk-home` would leave `java.lang.Object` unresolvable.
    arguments.jdkHome = arguments.jdkHome ?: request.modularJdkRoot?.path
    arguments.noJdk = arguments.jdkHome == null

    // A round that only removed sources still runs, so that the compiler rewrites the module mapping
    arguments.allowNoSourceFiles = true

    arguments.moduleName = request.moduleName
    arguments.commonSources = request.commonSources.map { it.path }.toTypedArray()
    arguments.friendPaths = (arguments.friendPaths.orEmpty().toList() + request.friendDirectories.map { it.path }).distinct().toTypedArray()
    val classpath = (arguments.classpath?.split(File.pathSeparator).orEmpty() + request.classpathRoots.map { it.path })
      .filter { it.isNotEmpty() }
      .distinct()
    arguments.classpath = classpath.takeIf { it.isNotEmpty() }?.joinToString(File.pathSeparator)

    // An argument without a typed option (`-P`, `-Xplugin`, ...) still round-trips through the string form
    val argumentStrings = (ArgumentUtils.convertArgumentsToStringList(arguments) + request.compilerSettings.additionalArgumentsAsList)
      .filterDuplicatedCompilerPluginOptions()
      .filterDuplicatedWarningLevels()
    if (KotlinBuilder.LOG.isDebugEnabled) {
      KotlinBuilder.LOG.debug("Compiler arguments of ${request.moduleName}: " + argumentStrings.joinToString(" "))
    }
    @Suppress("DEPRECATION")
    builder.compilerArguments.applyArgumentStrings(argumentStrings)

    val (javaSourceRoots, javaPackagePrefix) = javaSourceRootsAndPackagePrefix(request.javaSourceRoots)
    // The typed options are set last: the last write wins, and these describe the target, not the user settings
    with(builder.compilerArguments) {
      this[JvmCompilerArguments.X_ALLOW_NO_SOURCE_FILES] = true
      this[JvmCompilerArguments.MODULE_NAME] = request.moduleName
      this[JvmCompilerArguments.NO_STDLIB] = true
      this[JvmCompilerArguments.NO_REFLECT] = true
      this[JvmCompilerArguments.X_JAVA_PACKAGE_PREFIX] = javaPackagePrefix
      this[JvmCompilerArguments.NO_JDK] = arguments.noJdk
      this[JvmCompilerArguments.JDK_HOME] = arguments.jdkHome?.let { Paths.get(it) }
      this[JvmCompilerArguments.CLASSPATH] = classpath.map { Paths.get(it) }.ifEmpty { null }
      this[JvmCompilerArguments.X_JAVA_SOURCE_ROOTS] = javaSourceRoots.map { it.toPath() }
      this[JvmCompilerArguments.X_FRIEND_PATHS] = arguments.friendPaths.orEmpty().map { Paths.get(it) }
    }
  }

  /**
   * `-Xjava-package-prefix` applies to all Java source roots, while JPS has a prefix per root. When the prefixes differ,
   * the Java files of the roots with a prefix are passed one by one: the compiler reads the package of a single Java file
   * from the file itself.
   */
  private fun javaSourceRootsAndPackagePrefix(roots: List<JvmSourceRoot>): Pair<List<File>, String?> {
    val prefixes = roots.map { it.packagePrefix }.distinct()
    if (prefixes.size <= 1) return roots.map { it.file } to prefixes.singleOrNull()

    val files = roots.flatMap { (file, packagePrefix) ->
      if (packagePrefix == null) listOf(file)
      else file.walk().filter { it.isFile && it.extension == "java" }.toList()
    }
    return files to null
  }

  /** Keeps the first `-P plugin:<id>:<option>=<value>` of each option. */
  private fun List<String>.filterDuplicatedCompilerPluginOptions(): List<String> {
    val result = ArrayList<String>(size)
    val knownPluginOptions = HashSet<String>()
    val iterator = iterator()
    while (iterator.hasNext()) {
      val argument = iterator.next()
      if (argument == "-P" && iterator.hasNext()) {
        val pluginOption = iterator.next()
        if (knownPluginOptions.add(pluginOption)) {
          result.add(argument)
          result.add(pluginOption)
        }
      }
      else {
        result.add(argument)
      }
    }
    return result
  }

  /** Keeps the first occurrence of each `-Xwarning-level=<diagnostic>:<level>`. */
  private fun List<String>.filterDuplicatedWarningLevels(): List<String> {
    val known = HashSet<String>()
    return filter { !it.startsWith("-Xwarning-level") || known.add(it) }
  }

  /**
   * The Build Tools API offers push cancellation, JPS offers a pull-style cancel status. A poller bridges the two for
   * the duration of the operation.
   */
  private fun <R> withCancellationWatchdog(operation: JvmCompilationOperation, body: () -> R): R {
    val finished = AtomicBoolean(false)
    val watchdog = Thread({
      while (!finished.get()) {
        if (context.cancelStatus.isCanceled) {
          operation.cancel()
          return@Thread
        }
        try {
          Thread.sleep(CANCELLATION_POLL_INTERVAL_MS)
        }
        catch (_: InterruptedException) {
          return@Thread
        }
      }
    }, "Kotlin JPS builder cancellation watchdog")
    watchdog.isDaemon = true
    watchdog.start()

    return try {
      body()
    }
    finally {
      finished.set(true)
      watchdog.interrupt()
    }
  }

  private companion object {
    const val CANCELLATION_POLL_INTERVAL_MS = 200L
  }
}

/**
 * Holds the error messages of one daemon attempt back. A single error message fails the whole JPS build, so a
 * compilation that falls back to the build process must not leave the error behind. The other messages go through at
 * once, so the build log stays live.
 */
private class DaemonAttemptMessageSink(val delegate: CompilationMessageSink) : CompilationMessageSink {
  private val heldErrors = ArrayDeque<Pair<String, () -> Unit>>()

  override val hasErrors: Boolean
    get() = delegate.hasErrors || heldErrors.isNotEmpty()

  override val isDebugEnabled: Boolean
    get() = delegate.isDebugEnabled

  override fun render(severity: CompilerMessageRenderer.Severity, message: String, location: CompilerMessageRenderer.SourceLocation?): String {
    if (severity != CompilerMessageRenderer.Severity.ERROR) return delegate.render(severity, message, location)
    heldErrors.add(message to { delegate.render(severity, message, location) })
    return ""
  }

  override fun error(msg: String, throwable: Throwable?) {
    heldErrors.add(msg to { delegate.error(msg, throwable) })
  }

  override fun warn(msg: String) = delegate.warn(msg)

  override fun warn(msg: String, throwable: Throwable?) = delegate.warn(msg, throwable)

  override fun info(msg: String) = delegate.info(msg)

  override fun debug(msg: String) = delegate.debug(msg)

  override fun lifecycle(msg: String) = delegate.lifecycle(msg)

  override fun reportError(message: String) {
    heldErrors.add(message to { delegate.reportError(message) })
  }

  /** Sends the held errors to the delegate, when the compilation does not run again. */
  fun flush() {
    while (heldErrors.isNotEmpty()) heldErrors.removeFirst().second()
  }

  /** Drops the held errors and logs them, when the compilation runs again in process. */
  fun discard() {
    while (heldErrors.isNotEmpty()) {
      KotlinBuilder.LOG.info("Kotlin daemon error: " + heldErrors.removeFirst().first)
    }
  }
}
