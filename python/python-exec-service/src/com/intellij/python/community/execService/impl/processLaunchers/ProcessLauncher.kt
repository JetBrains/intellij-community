// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.impl.base.ProcessFunctions
import com.intellij.platform.eel.pathSeparator
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.TtySize
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.impl.LoggingProcess
import com.intellij.python.processOutput.common.ProcessOutputTopic
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import kotlinx.coroutines.CoroutineScope
import java.util.Locale
import kotlin.time.Clock

private val logger = fileLogger()

internal class ProcessLauncher(
  val exeForError: Exe,
  val args: List<String>,
  private val processCommands: ProcessCommands,
) {
  suspend fun start(weight: ConcurrentProcessWeight? = null): Result<LoggingProcess, ExecErrorReason.CantStart> =
    processCommands.start()
      .mapSuccess {
        LoggingProcess(
          it,
          weight,
          processCommands.scopeToBind.coroutineContext[TraceContext.Key],
          Clock.System.now(),
          processCommands.info.cwd,
          exeForError,
          args,
          processCommands.info.env,
          processCommands.info.target,
          ProcessOutputTopic,
        )
      }

  suspend fun killAndJoin() {
    processCommands.processFunctions.killAndJoin({ logger.warn(it) }, exeForError.toString())
  }
}

internal interface ProcessCommands {
  suspend fun start(): Result<Process, ExecErrorReason.CantStart>
  val processFunctions: ProcessFunctions
  val scopeToBind: CoroutineScope
  val info: ProcessCommandsInfo
}

internal data class ProcessCommandsInfo(
  val env: Map<String, String>,
  val cwd: String?,
  val target: String,
)

internal data class LaunchRequest(
  val scopeToBind: CoroutineScope,
  val args: Args,
  private val env: Map<String, String>,
  val usePty: TtySize?,
  val uploadConfig: UploadConfig? = null,
  val downloadConfig: DownloadConfig? = null,
) {
  /**
   * Returns the environment for the process.
   * It joins the environment of this request with [otherEnv].
   *
   * [otherEnv] holds the variables that [Args.getArgsAndEnv] creates for an uploaded file.
   * [family] is the operating system of the machine that runs the process.
   *
   * See [mergeEnvs] for the merge rule.
   */
  fun getEnvMergingWithPathVars(otherEnv: Map<String, String>, family: EelOsFamily): Map<String, String> =
    mergeEnvs(ourEnv = env, theirEnv = otherEnv, family = family)
}


/**
 * Joins [ourEnv] and [theirEnv] into one environment for the [family] operating system.
 *
 * A name that only one map has keeps its own value.
 * When both maps have the name, the result holds both values as one path list.
 * The [ourEnv] value comes first, then the [pathSeparator] of [family], then the [theirEnv] value.
 * The caller uses this rule for a path list variable such as `PATH` or `PYTHONPATH`.
 *
 * On [EelOsFamily.Windows] the name of a variable is case-insensitive.
 * `Path` and `PATH` are one variable, and the result keeps the name that it saw first.
 * On [EelOsFamily.Posix] the name is case-sensitive, so `Path` and `PATH` stay two variables.
 * This rule also applies to two names in the same map.
 *
 * An empty value adds nothing to the path list, so the result never has an empty part.
 * A name with only empty values stays in the result with an empty value.
 *
 * The result keeps the [ourEnv] names first, in their order.
 * The names that only [theirEnv] has come after them.
 */
internal fun mergeEnvs(
  ourEnv: Map<String, String>,
  theirEnv: Map<String, String>,
  family: EelOsFamily,
): Map<String, String> {
  val ignoreCase = when (family) {
    EelOsFamily.Posix -> false
    EelOsFamily.Windows -> true
  }
  // The key is the name in one fixed case. The value holds the first name and every value under it.
  val groups = LinkedHashMap<String, Pair<String, MutableList<String>>>(ourEnv.size + theirEnv.size)
  for ((name, value) in ourEnv.entries.asSequence() + theirEnv.entries.asSequence()) {
    val key = if (ignoreCase) name.uppercase(Locale.ROOT) else name
    val (_, values) = groups.getOrPut(key) { Pair(name, mutableListOf()) }
    if (value.isNotEmpty()) {
      values.add(value)
    }
  }
  val separator = family.pathSeparator
  return groups.values.associate { (name, values) -> name to values.joinToString(separator) }
}
