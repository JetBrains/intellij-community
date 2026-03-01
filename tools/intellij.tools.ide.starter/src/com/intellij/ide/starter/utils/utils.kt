package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ide.DEFAULT_DISPLAY_ID
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.io.createParentDirectories
import com.intellij.util.system.OS
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

const val beforeKillScreenshotName: String = "screenshotBeforeKill.jpg"

fun formatArtifactName(artifactType: String, testName: String): String {
  val testNameFormatted = testName.replace("/", "-").replace(" ", "")
  val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  return "$artifactType-$testNameFormatted-$time"
}

fun getThrowableText(t: Throwable): String {
  val writer = StringWriter()
  t.printStackTrace(PrintWriter(writer))
  return writer.buffer.toString()
}

/**
 * In case of success - return T
 * In case of error - print error to stderr and return null
 */
inline fun <T> catchAll(message: String = "", action: () -> T): T? = try {
  if (message.isNotEmpty()) logOutput("Performing '$message' with catching all exceptions")
  action()
}
catch (t: Throwable) {
  logError("CatchAll ${if (message.isNotEmpty()) "for '$message' " else ""} swallowed error: ${t.message}")
  logError(getThrowableText(t))
  null
}

fun String.withIndent(indent: String = "  "): String = lineSequence().map { "$indent$it" }.joinToString(System.lineSeparator())

fun takeScreenshot(logsDir: Path) {
  takeScreenshot(logsDir, beforeKillScreenshotName)
}

fun takeScreenshot(logsDir: Path, screenshotName: String, ignoreExceptions: Boolean = false) {
  val screenshotFile = logsDir.resolve(screenshotName)
  logOutput("Taking screenshot into ${screenshotFile.pathString}")
  val toolsDir = GlobalPaths.instance.getCacheDirectoryFor("tools")
  val toolName = "TakeScreenshot"
  val screenshotTool = toolsDir / toolName / "$toolName.jar"
  if (!screenshotTool.exists()) {
    val screenshotJar = IDERunContext::class.java.classLoader.getResourceAsStream("tools/$toolName.jar")!!
    screenshotJar.copyTo(screenshotTool.createParentDirectories().outputStream())
  }

  val javaPath = ProcessHandle.current().info().command().orElseGet {
    // for local runs on mac
    val javaHome = System.getProperty("java.home")
    val javaBin = if (OS.CURRENT == OS.Windows) "java.exe" else "java"
    Path.of(javaHome, "bin", javaBin).toString()
  }
  val stdOut = ExecOutputRedirect.ToStdOut("[take-screenshot-out]")
  val stdErr = ExecOutputRedirect.ToStdOut("[take-screenshot-err]")
  try {
    ProcessExecutor(
      presentableName = "take-screenshot",
      workDir = toolsDir,
      timeout = 15.seconds,
      stdoutRedirect = stdOut,
      stderrRedirect = stdErr,
      args = mutableListOf(javaPath, "-jar", screenshotTool.absolutePathString(), screenshotFile.toString()),
      environmentVariables = mapOf("DISPLAY" to (System.getenv("DISPLAY") ?: ":$DEFAULT_DISPLAY_ID")),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }
  catch (e: Exception) {
    if (!ignoreExceptions) throw e
    logOutput("Exception while taking screenshot: ${e.message}")
    return
  }

  if (screenshotFile.exists()) {
    logOutput("Screenshot saved in $screenshotFile")
  }
  else {
    error("Couldn't take screenshot")
  }
}


fun startProfileNativeThreads(pid: String) {
  if (!SystemInfo.isWindows) {
    val toolsDir = GlobalPaths.instance.getCacheDirectoryFor("tools")
    val toolName = when {
      SystemInfo.isMac -> "async-profiler-2.7-macos"
      SystemInfo.isLinux -> "async-profiler-2.7-linux-x64"
      else -> error("Not supported OS")
    }
    val profiler = toolsDir / toolName
    downloadAsyncProfilerIfNeeded(profiler, toolsDir)
    givePermissionsToExecutables(profiler)

    ProcessExecutor(
      presentableName = "start-profile",
      workDir = profiler,
      timeout = 15.seconds,
      args = mutableListOf("./profiler.sh", "start", pid)
    ).start()
  }
}

private fun givePermissionsToExecutables(profiler: Path) {
  ProcessExecutor(
    presentableName = "give-permissions-to-jattach",
    workDir = profiler.resolve("build"),
    timeout = 10.seconds,
    args = mutableListOf("chmod", "+x", "jattach")
  ).start()

  ProcessExecutor(
    presentableName = "give-permissions-to-profiler",
    workDir = profiler,
    timeout = 10.seconds,
    args = mutableListOf("chmod", "+x", "profiler.sh")
  ).start()
}

fun stopProfileNativeThreads(pid: String, fileToStoreInfo: String) {
  if (!SystemInfo.isWindows) {
    val toolsDir = GlobalPaths.instance.getCacheDirectoryFor("tools")
    val toolName = "async-profiler-2.7-macos"
    val profiler = toolsDir / toolName

    ProcessExecutor(
      presentableName = "stop-profile",
      workDir = profiler,
      timeout = 15.seconds,
      args = mutableListOf("./profiler.sh", "stop", pid, "-f", fileToStoreInfo)
    ).start()
  }
}

private fun downloadAsyncProfilerIfNeeded(profiler: Path, toolsDir: Path) {
  if (!File(profiler.toString()).exists()) {
    val profilerFileName = when {
      SystemInfo.isMac -> "async-profiler-2.7-macos.zip"
      SystemInfo.isLinux -> "async-profiler-2.7-linux-x64.tar.gz"
      else -> error("Current OS is not supported")
    }
    val archivePath = toolsDir / profilerFileName
    HttpClient.download("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.7/$profilerFileName",
                        archivePath)
    FileSystem.unpack(archivePath, toolsDir)
  }
}

fun pathInsideJarFile(
  jarFile: Path,
  pathInsideJar: String,
): String = jarFile.toAbsolutePath().toString().trimEnd('/') + "!/" + pathInsideJar

private val dockerPathRegex = Regex("^[\\\\/]+docker.*")

fun Path.isOnDocker(): Boolean = this.toString().matches(dockerPathRegex)