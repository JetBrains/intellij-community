package com.intellij.ide.starter.exec

import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration

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

/**
 * Creates new process and wait for it's completion
 */
@Throws(ExecTimeoutException::class)
fun exec(
  presentablePurpose: String,
  workDir: Path?,
  timeout: Duration = Duration.minutes(10),
  environmentVariables: Map<String, String> = System.getenv(),
  args: List<String>,
  errorDiagnosticFiles: List<Path> = emptyList(),
  stdoutRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
  stderrRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
  onProcessCreated: (Process, Long) -> Unit = { _, _ -> },
  onBeforeKilled: (Process, Long) -> Unit = { _, _ -> },
  stdInBytes: ByteArray = byteArrayOf(),
  onlyEnrichExistedEnvVariables: Boolean = false
) {
  logOutput(buildString {
    appendLine("Running external process for `$presentablePurpose`")
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
      Process: `$presentablePurpose`
      Arguments: ${args.joinToString(separator = " ")}
      Environment variables: [${processBuilder.environment().entries.joinToString { "${it.key}=${it.value}" }}]
    """.trimIndent())
  val process = processBuilder.start()

  val processId = process.pid()
  catchAll {
    logOutput("  ... started external process `$presentablePurpose` with process ID = $processId")
    onProcessCreated(process, processId)
  }

  val inputThread = redirectProcessInput(process, stdInBytes)
  val stdoutThread = redirectProcessOutput(process, true, stdoutRedirect)
  val stderrThread = redirectProcessOutput(process, false, stderrRedirect)
  val threads = listOfNotNull(inputThread, stdoutThread, stderrThread)

  fun killProcess() {
    catchAll { onBeforeKilled(process, processId) }
    catchAll { process.descendants().forEach { catchAll { it.destroyForcibly() } } }
    catchAll { process.destroy() }
    catchAll { process.destroyForcibly() }
    catchAll { threads.forEach { it.interrupt() } }
  }

  val stopper = Runnable {
    logOutput(
      "   ... terminating process `$presentablePurpose` by request from external process (either SIGTERM or SIGKILL is caught) ...")
    killProcess()
  }

  val stopperThread = Thread(stopper, "process-shutdown-hook")
  try {
    Runtime.getRuntime().addShutdownHook(stopperThread)
  }
  catch (e: IllegalStateException) {
    logError("Process: $presentablePurpose. Shutdown hook cannot be added because: ${e.message}")
  }

  try {
    if (!runCatching { process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS) }.getOrDefault(false)) {
      stopperThread.apply {
        start()
        join(Duration.seconds(20).inWholeMilliseconds)
      }
      throw ExecTimeoutException(args.joinToString(" "), timeout)
    }
  }
  finally {
    catchAll { Runtime.getRuntime().removeShutdownHook(stopperThread) }
  }

  threads.forEach { catchAll { it.join() } }

  val code = process.exitValue()
  if (code != 0) {
    val linesLimit = 100

    logOutput("  ... failed external process `$presentablePurpose` with exit code $code")
    val message = buildString {
      appendLine("External process `$presentablePurpose` failed with code $code")
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
  logOutput("  ... successfully finished external process for `$presentablePurpose` with exit code 0")
}

class ExecTimeoutException(private val processName: String,
                           private val timeout: Duration) : RuntimeException() {
  override val message
    get() = "Failed to wait for the process `$processName` to complete in $timeout"
}

fun executeScript(fileNameToExecute: String, projectDirPath: Path) {
  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()

  exec(
    presentablePurpose = "Executing of $fileNameToExecute",
    workDir = projectDirPath,
    timeout = Duration.minutes(20),
    args = listOf(fileNameToExecute),
    stdoutRedirect = stdout,
    stderrRedirect = stderr
  )

  val commit = stdout.read().trim()
  val error = stderr.read().trim()

  logOutput("Stdout of command execution $commit")
  logOutput("Stderr of command execution $error")
}

fun execGradlew(pathToProject: Path, args: List<String>) {
  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()

  val command = when (SystemInfo.isWindows) {
    true -> (pathToProject / "gradlew.bat").toString()
    false -> "./gradlew"
  }

  if (!SystemInfo.isWindows) {
    exec(
      presentablePurpose = "chmod gradlew",
      workDir = pathToProject,
      timeout = Duration.minutes(1),
      args = listOf("chmod", "+x", "gradlew"),
      stdoutRedirect = stdout,
      stderrRedirect = stderr
    )
  }
  exec(
    presentablePurpose = "Gradle Format",
    workDir = pathToProject,
    timeout = Duration.minutes(1),
    args = listOf(command) + args,
    stdoutRedirect = stdout,
    stderrRedirect = stderr
  )
}
