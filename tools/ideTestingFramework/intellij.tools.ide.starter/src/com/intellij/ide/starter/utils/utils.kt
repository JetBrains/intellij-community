package com.intellij.ide.starter.utils

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.system.SystemInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.Charset
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
inline fun <T> catchAll(action: () -> T): T? {
  try {
    return action()
  }
  catch (t: Throwable) {
    logError("CatchAll swallowed error: ${t.message}")
    logError(getThrowableText(t))
    return null
  }
}

fun FileStore.getDiskInfo(): String = buildString {
  appendLine("Disk info of ${name()}")
  appendLine("  Total space: " + totalSpace.formatSize())
  appendLine("  Unallocated space: " + unallocatedSpace.formatSize())
  appendLine("  Usable space: " + usableSpace.formatSize())
}

fun Runtime.getRuntimeInfo(): String = buildString {
  appendLine("Memory info")
  appendLine("  Total memory: " + totalMemory().formatSize())
  appendLine("  Free memory: " + freeMemory().formatSize())
  appendLine("  Max memory: " + maxMemory().formatSize())
}

/**
 * Invoke cmd: java [arg1 arg2 ... argN]
 */
fun execJavaCmd(javaHome: Path, args: Iterable<String> = listOf()): List<String> {
  val ext = if (SystemInfo.isWindows) ".exe" else ""
  val realJavaHomePath = if (javaHome.isSymbolicLink()) javaHome.readSymbolicLink() else javaHome

  val java = realJavaHomePath.toAbsolutePath().resolve("bin/java$ext")
  require(java.isRegularFile()) { "Java is not found under $java" }

  val prefix = "exec-java-cmd"
  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()

  val processArguments = listOf(java.toString()).plus(args)

  ProcessExecutor(
    presentableName = prefix,
    workDir = javaHome,
    timeout = 1.minutes,
    args = processArguments,
    stdoutRedirect = stdout,
    stderrRedirect = stderr
  ).start()

  val mergedOutput = listOf(stdout, stderr)
    .flatMap { it.read().split(System.lineSeparator()) }
    .map { it.trim() }
    .filter { it.isNotBlank() }

  logOutput(
    """
    Result of calling $processArguments:
    ${mergedOutput.joinToString(System.lineSeparator())}
    """)
  return mergedOutput
}

/**
 * Invoke java -version
 *
 * @return
 * openjdk version "17.0.1" 2021-10-19 LTS
 * OpenJDK Runtime Environment Corretto-17.0.1.12.1 (build 17.0.1+12-LTS)
 * OpenJDK 64-Bit Server VM Corretto-17.0.1.12.1 (build 17.0.1+12-LTS, mixed mode, sharing)
 */
fun callJavaVersion(javaHome: Path): String = execJavaCmd(javaHome, listOf("-version")).joinToString(System.lineSeparator())

fun isX64Jdk(javaHome: Path): Boolean {
  val archProperty = execJavaCmd(javaHome, listOf("-XshowSettings:all", "-version")).firstOrNull { it.startsWith("sun.arch.data.model") }
  if (archProperty.isNullOrBlank())
    throw IllegalAccessException("Couldn't get architecture property sun.arch.data.model value from JDK")

  return archProperty.trim().endsWith("64")
}

fun resolveInstalledJdk11(): Path {
  val jdkEnv = listOf("JDK_11_X64", "JAVA_HOME").firstNotNullOfOrNull { System.getenv(it) } ?: System.getProperty("java.home")
  val javaHome = jdkEnv?.let {
    val path = Path(it)
    if (path.isSymbolicLink()) path.readSymbolicLink()
    else path
  }

  if (javaHome == null || !javaHome.exists())
    throw IllegalArgumentException("Java Home $javaHome is null, empty or doesn't exist. Specify JAVA_HOME to point to JDK 11 x64")

  require(javaHome.isDirectory() && Files.walk(javaHome)
    .use { it.count() } > 10) {
    "Java Home $javaHome is not found or empty!"
  }

  require(isX64Jdk(javaHome)) { "JDK at path $javaHome should support x64 architecture" }
  return javaHome
}

fun String.withIndent(indent: String = "  "): String = lineSequence().map { "$indent$it" }.joinToString(System.lineSeparator())

private fun quoteArg(arg: String): String {

  val specials = " #'\"\n\r\t\u000c"
  if (!specials.any { arg.contains(it) }) {
    return arg
  }

  val sb = StringBuilder(arg.length * 2)
  for (element in arg) {
    when (element) {
      ' ', '#', '\'' -> sb.append('"').append(element).append('"')
      '"' -> sb.append("\"\\\"\"")
      '\n' -> sb.append("\"\\n\"")
      '\r' -> sb.append("\"\\r\"")
      '\t' -> sb.append("\"\\t\"")
      else -> sb.append(element)
    }
  }

  return sb.toString()
}

/**
 * Writes list of Java arguments to the Java Command-Line Argument File
 * See https://docs.oracle.com/javase/9/tools/java.htm, section "java Command-Line Argument Files"
 **/
fun writeJvmArgsFile(argFile: Path,
                     args: List<String>,
                     lineSeparator: String = System.lineSeparator(),
                     charset: Charset = Charsets.UTF_8) {
  Files.newBufferedWriter(argFile, charset).use { writer ->
    for (arg in args) {
      writer.write(quoteArg(arg))
      writer.write(lineSeparator)
    }
  }
}

fun takeScreenshot(logsDir: Path) {
  val toolsDir = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("tools")
  val toolName = "TakeScreenshot"
  val screenshotTool = toolsDir / toolName
  if (!File(screenshotTool.toString()).exists()) {
    val archivePath = toolsDir / "$toolName.zip"
    HttpClient.download("https://repo.labs.intellij.net/phpstorm/tools/TakeScreenshot-1.02.zip", archivePath)
    FileSystem.unpack(archivePath, screenshotTool)
  }
  val screenshotFile = logsDir.resolve("screenshot_beforeKill.jpg")

  val toolPath = screenshotTool.resolve("$toolName.jar")
  val javaPath = ProcessHandle.current().info().command().orElseThrow().toString()
  ProcessExecutor(
    presentableName = "take-screenshot",
    workDir = toolsDir,
    timeout = 15.seconds,
    args = mutableListOf(javaPath, "-jar", toolPath.absolutePathString(), screenshotFile.toString()),
    environmentVariables = mapOf("DISPLAY" to ":88"),
    onlyEnrichExistedEnvVariables = true
  ).start()

  if (screenshotFile.exists()) {
    logOutput("Screenshot saved in $screenshotFile")
  }
  else {
    error("Couldn't take screenshot")
  }
}

fun collectJBRDiagnosticFilesIfExist(context: IDETestContext) {
  val userHome = System.getProperty("user.home")
  val pathUserHome = Paths.get(userHome)
  val listOfJavaErrorFiles = pathUserHome.toFile().listFiles().filter { it.nameWithoutExtension.startsWith("java_error_in_idea_") && it.extension == "log" }
  if(listOfJavaErrorFiles.isNotEmpty()) {
    listOfJavaErrorFiles.forEach {
    if (!context.paths.jbrDiagnostic.resolve(it.name).toFile().exists())
      it.copyTo(context.paths.jbrDiagnostic.resolve(it.name).toFile())
    }
    context.publishArtifact(context.paths.jbrDiagnostic)
  }
}

fun startProfileNativeThreads(pid: String) {
  if (!SystemInfo.isWindows) {
    val toolsDir = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("tools")
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
    val toolsDir = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("tools")
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
  pathInsideJar: String
): String = jarFile.toAbsolutePath().toString().trimEnd('/') + "!/" + pathInsideJar

data class FindUsagesCallParameters(val pathToFile: String, val offset: String, val element: String) {
  override fun toString() = "$pathToFile $element, $offset)"
}
