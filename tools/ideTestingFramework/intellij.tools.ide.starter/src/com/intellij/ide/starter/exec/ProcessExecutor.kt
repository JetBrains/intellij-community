package com.intellij.ide.starter.exec

import com.intellij.ide.starter.coroutine.supervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.Runnable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProcessExecutor(val presentableName: String,
                      val workDir: Path?,
                      val timeout: Duration = 10.minutes,
                      val environmentVariables: Map<String, String> = System.getenv(),
                      val args: List<String>,
                      val errorDiagnosticFiles: List<Path> = emptyList(),
                      val stdoutRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
                      val stderrRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
                      val onProcessCreated: suspend (Process, Long) -> Unit = { _, _ -> },
                      val onBeforeKilled: suspend (Process, Long) -> Unit = { _, _ -> },
                      val stdInBytes: ByteArray = byteArrayOf(),
                      val onlyEnrichExistedEnvVariables: Boolean = false) {

  private fun redirectProcessOutput(
    process: Process,
    outOrErrStream: Boolean,
    redirectOutput: ExecOutputRedirect
  ): Thread {
    val inputStream = if (outOrErrStream) process.inputStream else process.errorStream
    return thread(start = true, isDaemon = true, name = "Redirect " + (if (outOrErrStream) "stdout" else "stderr")) {
      val reader = inputStream.bufferedReader()
      redirectOutput.open()
      try {
        while (true) {
          val line = try {
            reader.readLine() ?: break
          }
          catch (e: IOException) {
            break
          }
          redirectOutput.redirectLine(line)
        }
      }
      finally {
        redirectOutput.close()
      }
    }
  }

  private fun redirectProcessInput(process: Process, inputBytes: ByteArray): Thread? {
    if (inputBytes.isEmpty()) {
      catchAll { process.outputStream.close() }
      return null
    }

    return thread(start = true, isDaemon = true, name = "Redirect input") {
      catchAll {
        process.outputStream.use {
          it.write(inputBytes)
        }
      }
    }
  }

  private fun ProcessBuilder.actualizeEnvVariables(environmentVariables: Map<String, String> = System.getenv(),
                                                   onlyEnrichExistedEnvVariables: Boolean = false): ProcessBuilder {
    val processEnvironment = environment()
    if (processEnvironment == environmentVariables) return this

    environmentVariables.filter { it.value == null }.forEach { logError("Env variable: ${it.key} has null value ${it.value}") }
    val notNullValues = environmentVariables.filter { it.value != null }

    // env variables enrichment
    processEnvironment.putAll(notNullValues)

    if (!onlyEnrichExistedEnvVariables) {
      val missingKeys = processEnvironment.keys - notNullValues.keys
      missingKeys.forEach { key -> processEnvironment.remove(key) }
    }

    return this
  }

  private fun analyzeProcessExit(process: Process) {
    val code = process.exitValue()
    if (code != 0) {
      val linesLimit = 100

      logOutput("  ... failed external process `$presentableName` with exit code $code")
      val message = buildString {
        appendLine("External process `$presentableName` failed with code $code")
        for (diagnosticFile in errorDiagnosticFiles.filter { it.exists() && Files.size(it) > 0 }) {
          appendLine(diagnosticFile.fileName.toString())
          appendLine(diagnosticFile.readText().lines().joinToString(System.lineSeparator()) { "  $it" })
        }

        stderrRedirect.read().lines().apply {
          take(linesLimit).dropWhile { it.trim().isBlank() }.let { lines ->
            if (lines.isNotEmpty()) {
              appendLine("  FIRST $linesLimit lines of the standard error stream")
              lines.forEach { appendLine("    $it") }
            }
          }

          if (size > linesLimit) {
            appendLine("...")

            takeLast(linesLimit).dropWhile { it.trim().isBlank() }.let { lines ->
              if (lines.isNotEmpty()) {
                appendLine("  LAST $linesLimit lines of the standard error stream")
                lines.forEach { appendLine("    $it") }
              }
            }
          }
        }

        stdoutRedirect.read().lines().takeLast(linesLimit).dropWhile { it.trim().isEmpty() }.let { lines ->
          if (lines.isNotEmpty()) {
            appendLine("  LAST $linesLimit lines of the standard output stream")
            lines.forEach { appendLine("    $it") }
          }
        }
      }
      error(message)
    }

    logOutput("  ... successfully finished external process for `$presentableName` with exit code 0")
  }

  /**
   * Creates new process and wait for it's completion
   */
  @Throws(ExecTimeoutException::class)
  fun start() {
    logOutput(buildString {
      appendLine("Running external process for `$presentableName`")
      appendLine("  Working directory: $workDir")
      appendLine("  Arguments: [${args.joinToString()}]")
      appendLine("  STDOUT will be redirected to: $stdoutRedirect")
      appendLine("  STDERR will be redirected to: $stderrRedirect")
      append("  STDIN is empty: " + stdInBytes.isEmpty())
    })

    require(args.isNotEmpty()) { "Arguments must be not empty to start external process" }

    val processBuilder = ProcessBuilder()
      .directory(workDir?.toFile())
      .command(*args.toTypedArray())
      .redirectInput(ProcessBuilder.Redirect.PIPE)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .actualizeEnvVariables(environmentVariables, onlyEnrichExistedEnvVariables)

    logOutput(
      """
      Process: `$presentableName`
      Arguments: ${args.joinToString(separator = " ")}
      Environment variables: [${processBuilder.environment().entries.joinToString { "${it.key}=${it.value}" }}]
    """.trimIndent())
    val process = processBuilder.start()

    val processId = process.pid()
    val onProcessCreatedJob: Job? = catchAll {
      logOutput("  ... started external process `$presentableName` with process ID = $processId")
      supervisorScope.launch { onProcessCreated(process, processId) }
    }

    val inputThread = redirectProcessInput(process, stdInBytes)
    val stdoutThread = redirectProcessOutput(process, true, stdoutRedirect)
    val stderrThread = redirectProcessOutput(process, false, stderrRedirect)
    val ioThreads = listOfNotNull(inputThread, stdoutThread, stderrThread)

    fun killProcess() {
      catchAll { runBlocking { withTimeout(1.minutes) { onBeforeKilled(process, processId) } } }
      catchAll { runBlocking { onProcessCreatedJob?.cancelAndJoin() } }
      catchAll { process.descendants().forEach { catchAll { it.destroyForcibly() } } }
      catchAll { process.destroy() }
      catchAll { process.destroyForcibly() }
      catchAll { ioThreads.forEach { it.interrupt() } }
    }

    val stopper = Runnable {
      logOutput(
        "   ... terminating process `$presentableName` by request from external process (either SIGTERM or SIGKILL is caught) ...")
      killProcess()
    }

    val stopperThread = Thread(stopper, "process-shutdown-hook")
    try {
      Runtime.getRuntime().addShutdownHook(stopperThread)
    }
    catch (e: IllegalStateException) {
      logError("Process: $presentableName. Shutdown hook cannot be added because: ${e.message}")
    }

    try {
      if (!runCatching { process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS) }.getOrDefault(false)) {
        stopperThread.apply {
          start()
          join(20.seconds.inWholeMilliseconds)
        }
        throw ExecTimeoutException(args.joinToString(" "), timeout)
      }
    }
    finally {
      catchAll { Runtime.getRuntime().removeShutdownHook(stopperThread) }
    }

    ioThreads.forEach { catchAll { it.join() } }

    analyzeProcessExit(process)
  }
}