// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.StdInConsumer
import com.intellij.python.community.execService.ZeroCodeStdoutParserTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformerBool
import com.intellij.python.community.execService.ZeroCodeStdoutTransformerTyped
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executePythonAdvanced
import com.intellij.python.community.execService.python.impl.PyExecPythonBundle.message
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.psi.LanguageLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

private const val SYS_MODULE = "sys"
private const val IS_GIL_ENABLED_FUNCTION = "_is_gil_enabled"

/**
 * The options that keep the machine of the user out of the answer.
 *
 * `-S` does not import `site`, so a `.pth` file runs no startup hook. Such a hook can reach a network, and it ran on
 * every start of the interpreter before, see PY-88315. The command needs nothing that `site` provides: the version and
 * the threading model come from the interpreter itself, and the kind of environment comes from the file system layout.
 * `-E` and `-s` drop the `PYTHON*` variables and the site directory of the user, and `-B` writes no `.pyc`.
 *
 * `-I` says all of this in one option, but Python 2.7 does not know it, and this still reports a 2.7 interpreter.
 */
private val PYTHON_INFO_ARGS = arrayOf("-E", "-s", "-S", "-B", "-c")

/**
 * Prints the version of the interpreter on one line, and `True` when the GIL is on, on the next line.
 *
 * One process answers both questions. An environment can run a startup hook on each start of the interpreter, so
 * the number of starts matters, see PY-88315. `sys.version` also removes the need for `--version`, which Python 2
 * writes to `stderr`, see https://bugs.python.org/issue18338.
 */
@Language("Python")
private const val PYTHON_INFO_CMD =
  "from __future__ import print_function; import $SYS_MODULE; " +
  "print($SYS_MODULE.version.split()[0]); " +
  "print($SYS_MODULE.$IS_GIL_ENABLED_FUNCTION()) " +
  "if hasattr($SYS_MODULE, '$IS_GIL_ENABLED_FUNCTION') and callable(getattr($SYS_MODULE, '$IS_GIL_ENABLED_FUNCTION')) " +
  "else print(True)"

@ApiStatus.Internal
internal suspend fun ExecService.validatePythonAndGetInfoImpl(python: ExecutablePython): PyResult<PythonInfo> =
  withContext(Dispatchers.IO) {
    val handler = transformerToHandler(processOutputTransformer = ZeroCodeStdoutParserTransformer { stdout ->
      parsePythonInfo(python, stdout)
    })
    val args = Args(*PYTHON_INFO_ARGS, PYTHON_INFO_CMD)
    val pythonInfo = executePythonAdvanced(python, args, ExecOptions(timeout = 1.minutes), handler)
      .getOr(message("python.cannot.exec", python.userReadableName)) { return@withContext it }
    return@withContext Result.success(pythonInfo)
  }

/**
 * Reads the two lines that [PYTHON_INFO_CMD] prints.
 *
 * The last two lines, because anything an environment prints of its own comes first, while the interpreter starts.
 */
private fun parsePythonInfo(python: ExecutablePython, stdout: String): Result<PythonInfo, @NlsSafe String?> {
  val lines = stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(2)
  val version = if (lines.size == 2) lines[0] else null
  val languageLevel = version?.let { LanguageLevel.fromPythonVersionSafe(it) }
                      ?: return Result.failure(message("python.get.version.wrong.version", python.userReadableName, stdout))
  val gilEnabled = when (lines[1]) {
    "True" -> true
    "False" -> false
    else -> return Result.failure(message("python.check.threading.fail"))
  }
  return Result.success(PythonInfo(languageLevel, freeThreaded = !gilEnabled, version = version))
}

@ApiStatus.Internal
internal suspend fun ExecService.execGetStdoutBoolImpl(python: ExecutablePython, command: @NlsSafe String): PyResult<Boolean> =
  execGetStdoutImpl(python, command, ZeroCodeStdoutTransformerBool)

@ApiStatus.Internal
internal suspend fun <T : Any> ExecService.execGetStdoutImpl(
  python: ExecutablePython,
  command: @NlsSafe String,
  transformer: ZeroCodeStdoutTransformerTyped<T>,
): PyResult<T> = withContext(Dispatchers.IO) {
  val options = ExecOptions(timeout = 1.minutes)
  val result = executePythonAdvanced(
    python,
    Args("-c", command),
    processInteractiveHandler = transformerToHandler(processOutputTransformer = transformer),
    options = options
  ).getOr(message("python.cannot.exec", python.userReadableName)) { return@withContext it }
  return@withContext Result.success(result)
}

private val ExecutablePython.userReadableName: @NlsSafe String
  get() =
    (listOf(when (binary) {
              is BinOnEel -> binary.path.pathString
              is BinOnTarget -> binary
            }) + args).joinToString(" ")


internal fun StdInProvider.asChannelConsumer(): StdInConsumer = { stdin ->
  try {
    stdin.sendWholeBuffer(ByteBuffer.wrap(data))
    // The process waits for `EOF` on `stdin`, so it never ends until the channel is closed.
    stdin.close(null)
  }
  catch (e: IOException) {
    // A broken channel needs no close. See EelSendChannelException.
    onError(e)
  }
}
