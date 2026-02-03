package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logOutput
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readSymbolicLink
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object JvmUtils {
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

  fun resolveInstalledJdk(): Path {
    val jdkEnv = listOf("JDK_21_0", "JDK_17_0", "JDK_11_0", "JDK_HOME", "JAVA_HOME")
                   .mapNotNull { System.getenv(it) }
                   .filterNot { it.startsWith("%") && it.endsWith("%") } // sometimes TeamCity have unresolved references in JAVA_HOME
                   .firstOrNull()
                 ?: System.getProperty("java.home")

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

  fun getJavaClassCompileVersion(filePath: Path): String {
    val stdout = ExecOutputRedirect.ToString()
    val versionMatcher = "major version: [0-9]{2,3}".toRegex()
    ProcessExecutor(
      "get java class compile version",
      workDir = filePath.parent,
      timeout = 30.seconds,
      args = listOf("javap", "-verbose", filePath.fileName.toString()),
      stdoutRedirect = stdout
    ).start()

    return (versionMatcher.find(stdout.read())!!.value.split(": ").last().toInt() - 44).toString()
  }
}

fun Runtime.getRuntimeInfo(): String = buildString {
  appendLine("Memory info")
  appendLine("  Total memory: " + totalMemory().formatSize())
  appendLine("  Free memory: " + freeMemory().formatSize())
  appendLine("  Max memory: " + maxMemory().formatSize())
}