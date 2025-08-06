// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.execService.impl.Arg
import com.intellij.python.community.execService.impl.ExecServiceImpl
import com.intellij.python.community.execService.impl.PyExecBundle
import com.intellij.python.community.execService.impl.transformerToHandler
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


/**
 * Default service implementation
 */
fun ExecService(): ExecService = ExecServiceImpl


/**
 * There are two ways to execute binary:
 */
sealed interface BinaryToExec

/**
 * [path] on eel (Use it for anything but SSH).
 * [workDir] is pwd. As it should be on the same eel as [path] for most cases (except WSL), it is better not to set it at all.
 * Prefer full [path] over relative.
 */
data class BinOnEel(val path: Path, internal val workDir: Path? = null) : BinaryToExec

/**
 * Legacy Targets-based approach. Do not use it, unless you know what you are doing
 * if [target] "local" target is used
 */
data class BinOnTarget(internal val configureTargetCmdLine: (TargetedCommandLineBuilder) -> Unit, val target: TargetEnvironmentConfiguration?) : BinaryToExec {
  constructor(exePath: FullPathOnTarget, target: TargetEnvironmentConfiguration?) : this({ it.setExePath(exePath) }, target)
}


/**
 * Execute [binary] right directly on the eel it resides on.
 */
suspend fun ExecService.execGetStdout(
  binary: Path,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = execGetStdout(BinOnEel(binary), args, options, procListener)

/**
 * Execute [binary] right directly where it sits
 */
suspend fun ExecService.execGetStdout(
  binary: BinaryToExec,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = execute(
  binary = binary,
  args = args,
  options = options,
  processOutputTransformer = ZeroCodeStdoutTransformer,
  procListener = procListener
)


/**
 * Execute [binaryName] on [eelApi].
 * This [binaryName] will be searched in `PATH`
 */
suspend fun ExecService.execGetStdout(
  eelApi: EelApi,
  binaryName: String,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> {
  val binary = eelApi.exec.findExeFilesInPath(binaryName).firstOrNull()?.asNioPath()
               ?: return PyResult.localizedError(PyExecBundle.message("py.exec.fileNotFound", binaryName, eelApi.descriptor.machine.name))
  return execGetStdout(BinOnEel(binary), args, options, procListener)
}


/**
 * Execute [commandForShell] on [eelApi].
 * Shell is `cmd` for Windows and Bourne Shell for POSIX.
 */
suspend fun ExecService.execGetStdoutInShell(
  eelApi: EelApi,
  commandForShell: String,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> {
  val (shell, arg) = eelApi.exec.getShell()
  return execGetStdout(BinOnEel(shell.asNioPath()), Args(arg, commandForShell).add(args), options, procListener)
}

/**
 * Execute [binary] with [args] and get both stdout/stderr outputs if `errorCode != 0`, returns error otherwise.
 * Function collects output lines and reports them to [procListener] if set
 *
 * @param[args] command line arguments
 * @param[options]  customizable process run options like timeout or environment variables to use
 * @return stdout or error. It is recommended to put this error into [com.jetbrains.python.errorProcessing.ErrorSink], but feel free to match and process it.
 */
@CheckReturnValue
suspend fun <T> ExecService.execute(
  binary: BinaryToExec,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  return reportRawProgress { reporter ->
    val ansiDecoder = AnsiEscapeDecoder()
    val listener = procListener ?: PyProcessListener {
      when (it) {
        is ProcessEvent.ProcessStarted, is ProcessEvent.ProcessEnded -> Unit
        is ProcessEvent.ProcessOutput -> {
          val outType = when (it.stream) {
            ProcessEvent.OutputType.STDOUT -> ProcessOutputTypes.STDOUT
            ProcessEvent.OutputType.STDERR -> ProcessOutputTypes.STDERR
          }
          ansiDecoder.escapeText(it.line, outType) { text, _ ->
            @Suppress("HardCodedStringLiteral")
            reporter.text(text)
          }
        }
      }
    }
    executeAdvanced(binary, args, options, transformerToHandler(procListener
                                                                ?: listener, processOutputTransformer))
  }

}


/**
 * Error is an optional additionalMessage, that will be used instead of a default one for the [ExecError]
 */
typealias ProcessOutputTransformer<T> = (EelProcessExecutionResult) -> Result<T, @NlsSafe String?>

object ZeroCodeStdoutTransformer : ProcessOutputTransformer<String> {
  override fun invoke(processOutput: EelProcessExecutionResult): Result<String, String?> =
    if (processOutput.exitCode == 0) Result.success(processOutput.stdoutString.trim()) else Result.failure(null)
}


/**
 * @property[env] Environment variables to be applied with the process run
 * @property[timeout] Process gets killed after this timeout
 * @property[processDescription] optional description to be displayed to user
 */
data class ExecOptions(
  val env: Map<String, String> = emptyMap(),
  val processDescription: @Nls String? = null,
  val timeout: Duration = 5.minutes,
)

/**
 * See [Args.addLocalFile]
 */
fun interface FileArgGenerator {
  fun generateArg(remoteFile: String): String
}


/**
 * ```kotlin
 *   val args = Args()
 *   args.addLocalFile(helper)
 *   args.addTextArgs("-v")
 * ```
 */
class Args(vararg initialArgs: String) {
  private val _args = CopyOnWriteArrayList<Arg>(initialArgs.map { Arg.StringArg(it) })
  fun addArgs(vararg args: String): Args {
    _args.addAll(args.map { Arg.StringArg(it) })
    return this
  }

  /**
   * This file will be copied to remote machine and its remote name will be added to the list of arguments.
   * Use [argGenerator] to modify name
   */
  fun addLocalFile(localFile: Path, argGenerator: FileArgGenerator = FileArgGenerator { it }): Args {
    _args.add(Arg.FileArg(localFile, argGenerator))
    return this
  }

  fun add(another: Args): Args {
    _args.addAll(another._args)
    return this
  }

  internal val localFiles: List<Path>
    get() = _args.mapNotNull {
      when (it) {
        is Arg.FileArg -> it.file
        is Arg.StringArg -> null
      }
    }

  internal suspend fun getArgs(mapFileToRemote: suspend (local: Path) -> String): List<String> =
    _args.map {
      when (it) {
        is Arg.StringArg -> it.arg
        is Arg.FileArg -> it.generator.generateArg(mapFileToRemote(it.file))
      }
    }
}

fun Args.addArgs(args: List<String>): Args = addArgs(*args.toTypedArray())