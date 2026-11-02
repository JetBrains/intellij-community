// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.config.CompilerRunnerConstants
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import java.io.File

/**
 * Reports what the compiler and the Build Tools API say as JPS build messages.
 *
 * The compiler diagnostics arrive through [CompilerMessageRenderer]. The renderer returns a blank string, so the
 * implementation does not log the diagnostic a second time. Argument validation errors arrive through [KotlinLogger].
 * Both paths report an error as a JPS error message, which fails the build.
 */
class JpsCompilerMessageSink(
  private val context: CompileContext,
  private val target: KotlinModuleBuildTarget?,
) : CompilerMessageRenderer, KotlinLogger {
  var hasErrors: Boolean = false
    private set

  val filesWithErrors: MutableSet<String> = LinkedHashSet()

  override val isDebugEnabled: Boolean
    get() = KotlinBuilder.LOG.isDebugEnabled

  override fun render(severity: CompilerMessageRenderer.Severity, message: String, location: CompilerMessageRenderer.SourceLocation?): String {
    val kind = when (severity) {
      CompilerMessageRenderer.Severity.ERROR -> BuildMessage.Kind.ERROR
      CompilerMessageRenderer.Severity.WARNING -> BuildMessage.Kind.WARNING
      CompilerMessageRenderer.Severity.INFO -> BuildMessage.Kind.INFO
      CompilerMessageRenderer.Severity.DEBUG -> null
    }
    report(kind, message, location)
    return ""
  }

  fun reportError(@Nls message: String) {
    report(BuildMessage.Kind.ERROR, message, null)
  }

  fun reportWarning(@Nls message: String) {
    report(BuildMessage.Kind.WARNING, message, null)
  }

  fun reportInfo(@Nls message: String) {
    report(BuildMessage.Kind.INFO, message, null)
  }

  private fun report(kind: BuildMessage.Kind?, message: String, location: CompilerMessageRenderer.SourceLocation?) {
    if (kind == null) {
      val path = if (location != null) "${location.path}:${location.line}:${location.column}: " else ""
      KotlinBuilder.LOG.debug(path + message)
      return
    }

    if (kind == BuildMessage.Kind.ERROR) {
      hasErrors = true
      location?.let { filesWithErrors.add(it.path) }
    }

    // Name the target when a common source is compiled for several targets
    val prefix = if (location != null && target != null && target.isFromIncludedSourceRoot(File(location.path))) "[${target.module.name}] " else ""

    context.processMessage(
      CompilerMessage(
        CompilerRunnerConstants.KOTLIN_COMPILER_NAME,
        kind,
        prefix + message,
        location?.path,
        -1, -1, -1,
        location?.line?.toLong() ?: -1,
        location?.column?.toLong() ?: -1,
      )
    )
  }

  override fun error(msg: String, throwable: Throwable?) {
    report(BuildMessage.Kind.ERROR, msg, null)
    if (throwable != null) {
      KotlinBuilder.LOG.info(msg, throwable)
    }
  }

  override fun warn(msg: String, throwable: Throwable?) {
    report(BuildMessage.Kind.WARNING, msg, null)
    if (throwable != null) {
      KotlinBuilder.LOG.info(msg, throwable)
    }
  }

  override fun info(msg: String) {
    KotlinBuilder.LOG.info(msg)
  }

  override fun lifecycle(msg: String) {
    KotlinBuilder.LOG.info(msg)
  }

  override fun debug(msg: String) {
    KotlinBuilder.LOG.debug(msg)
  }
}
