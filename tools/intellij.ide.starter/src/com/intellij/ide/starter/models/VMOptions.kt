package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.InstalledIDE
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.ide.parseVMOptions
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.FileSystem.cleanPathFromSlashes
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.writeJvmArgsFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

/**
 * allows to combine VMOptions mapping functions easily by calling this function as
 * ```
 *    {}.andThen {} function
 * ```
 */
fun (VMOptions.() -> VMOptions).andThen(right: VMOptions.() -> VMOptions): VMOptions.() -> VMOptions = {
  val left = this@andThen
  this.left().right()
}


data class VMOptions(
  private val ide: InstalledIDE,
  private val data: List<String>,
  val env: Map<String, String>
) {
  override fun toString() = buildString {
    appendLine("VMOptions{")
    appendLine("  env=$env")
    for (line in data) {
      appendLine("  $line")
    }
    appendLine("} // VMOptions")
  }

  @Suppress("unused")
  fun addSystemProperty(key: String, value: Boolean): VMOptions = addSystemProperty(key, value.toString())

  @Suppress("unused")
  fun addSystemProperty(key: String, value: Int): VMOptions = addSystemProperty(key, value.toString())

  @Suppress("unused")
  fun addSystemProperty(key: String, value: Long): VMOptions = addSystemProperty(key, value.toString())

  @Suppress("unused")
  fun addSystemProperty(key: String, value: Path): VMOptions = addSystemProperty(key, value.toAbsolutePath().toString())

  @Suppress("unused")
  fun addSystemProperty(key: String, value: String): VMOptions {
    System.setProperty(key, value) // to synchronize behaviour in IDEA and on test runner side
    return addLine(line = "-D$key=$value", filterPrefix = "-D$key=")
  }

  fun addLine(line: String, filterPrefix: String? = null): VMOptions {
    if (data.contains(line)) return this
    val copy = if (filterPrefix == null) data else data.filterNot { it.trim().startsWith(filterPrefix) }
    return copy(data = copy + line)
  }

  private fun filterKeys(toRemove: (String) -> Boolean) = copy(data = data.filterNot(toRemove))

  @Suppress("MemberVisibilityCanBePrivate")
  fun withEnv(key: String, value: String) = copy(env = env + (key to value))

  fun writeIntelliJVmOptionFile(path: Path) {
    path.writeLines(data)
    logOutput("Write vmoptions patch to $path")
  }

  fun diffIntelliJVmOptionFile(theFile: Path): VMOptionsDiff {
    val loadedOptions = parseVMOptions(this.ide, theFile).data
    return VMOptionsDiff(originalLines = this.data, actualLines = loadedOptions)
  }

  fun writeJavaArgsFile(theFile: File) {
    writeJvmArgsFile(theFile, this.data)
  }

  fun overrideDirectories(paths: IDEDataPaths) = this
    .addSystemProperty("idea.config.path", paths.configDir)
    .addSystemProperty("idea.system.path", paths.systemDir)
    .addSystemProperty("idea.plugins.path", paths.pluginsDir)
    .addSystemProperty("idea.log.path", paths.logsDir)

  fun enableStartupPerformanceLog(perf: IDEStartupReports): VMOptions {
    return this
      .addSystemProperty("idea.log.perf.stats.file", perf.statsJSON)
  }

  fun enableClassLoadingReport(filePath: Path): VMOptions {
    return this
      .addSystemProperty("idea.log.class.list.file", filePath)
      .addSystemProperty("idea.record.classpath.info", "true")
  }

  fun configureLoggers(
    debugLoggers: List<String> = emptyList(),
    traceLoggers: List<String> = emptyList()
  ): VMOptions {
    val withDebug = if (debugLoggers.isNotEmpty()) {
      this.addSystemProperty("idea.log.debug.categories", debugLoggers.joinToString(separator = ",") { "#" + it.removePrefix("#") })
    }
    else {
      this
    }
    return if (traceLoggers.isNotEmpty()) {
      withDebug.addSystemProperty("idea.log.trace.categories", traceLoggers.joinToString(separator = ",") { "#" + it.removePrefix("#") })
    }
    else {
      withDebug
    }
  }

  @Suppress("unused")
  fun debug(port: Int = 5005, suspend: Boolean = true): VMOptions {
    val suspendKey = if (suspend) "y" else "n"
    val configLine = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspendKey},address=*:${port}"
    return addLine(configLine, filterPrefix = "-agentlib:jdwp")
  }

  fun inHeadlessMode() = this
    .addSystemProperty("java.awt.headless", true)

  fun disableStartupDialogs() = this
    .addSystemProperty("jb.consents.confirmation.enabled", false)
    .addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")

  fun takeScreenshotIfFailure(logsDir: Path) = this
    .addSystemProperty("ide.performance.screenshot.before.kill", logsDir.resolve("screenshot_beforeKill.jpg").toString())

  fun installTestScript(testName: String,
                        paths: IDEDataPaths,
                        commands: Iterable<MarshallableCommand>): VMOptions {
    val scriptText = commands.joinToString(separator = System.lineSeparator()) { it.storeToString() }

    val scriptFileName = testName.cleanPathFromSlashes(replaceWith = "_") + ".text"
    val scriptFile = paths.systemDir.resolve(scriptFileName).apply {
      parent.createDirectories()
    }
    scriptFile.writeText(scriptText)

    return this.addSystemProperty("testscript.filename", scriptFile)
      // Use non-success status code 1 when running IDE as command line tool.
      .addSystemProperty("testscript.must.exist.process.with.non.success.code.on.ide.error", "true")
      // No need to report TeamCity test failure from within test script.
      .addSystemProperty("testscript.must.report.teamcity.test.failure.on.error", "false")
  }

  /** @see com.intellij.startupTime.StartupTimeWithCDSonJDK13.runOnJDK13 **/
  fun withCustomJRE(jre: Path): VMOptions {
    if (SystemInfo.isLinux) {
      val jrePath = jre.toAbsolutePath().toString()
      val envKey = when (ide.productCode) {
        "IU" -> "IDEA_JDK"
        "WS" -> "WEBIDE_JDK"
        else -> error("Not supported for product $ide")
      }
      return this.withEnv(envKey, jrePath)
    }

    if (SystemInfo.isMac) {
      //Does not work -- https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under
      //see https://youtrack.jetbrains.com/issue/IDEA-223075
      //see Launcher.m:226
      val jrePath = jre.toAbsolutePath().toString()
      val envKey = when (ide.productCode) {
        "IU" -> "IDEA_JDK"
        "WS" -> "WEBSTORM_JDK"
        else -> error("Not supported for product $ide")
      }
      return this.withEnv(envKey, jrePath)
    }

    if (SystemInfo.isWindows) {
      //see WinLauncher.rc and WinLauncher.cpp:294
      //see https://youtrack.jetbrains.com/issue/IDEA-223348
      val jrePath = jre.toRealPath().toString().replace("/", "\\")
      val envKey = when (ide.productCode) {
        "IU" -> "IDEA_JDK_64"
        "WS" -> "WEBIDE_JDK_64"
        else -> error("Not supported for product $ide")
      }
      return this.withEnv(envKey, jrePath)
    }

    error("Current OS is not supported")
  }

  fun usingStartupFramework() = this
    .addSystemProperty("startup.performance.framework", true)

  fun setFlagIntegrationTests() = this
    .addSystemProperty("idea.is.integration.test", true)

  fun setFatalErrorNotificationEnabled() = this
    .addSystemProperty("idea.fatal.error.notification", true)

  fun withJvmCrashLogDirectory(jvmCrashLogDirectory: Path) = this
    .addLine("-XX:ErrorFile=${jvmCrashLogDirectory.toAbsolutePath()}${File.separator}java_error_in_idea_%p.log", "-XX:ErrorFile=")

  fun withHeapDumpOnOutOfMemoryDirectory(directory: Path) = this
    .addLine("-XX:HeapDumpPath=${directory.toAbsolutePath()}", "-XX:HeapDumpPath=")

  fun withXmx(sizeMb: Int) = this
    .addLine("-Xmx" + sizeMb + "m", "-Xmx")

  @Suppress("unused")
  fun withG1GC() = this
    .filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    .filterKeys { it == "-XX:+UseG1GC" }
    .addLine("-XX:+UseG1GC")

  /** see https://openjdk.java.net/jeps/318 **/
  @Suppress("unused")
  fun withEpsilonGC() = this
    .filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    .filterKeys { it == "-XX:+UseG1GC" }
    .addLine("-XX:+UnlockExperimentalVMOptions")
    .addLine("-XX:+UseEpsilonGC")
    .addLine("-Xmx16g", "-Xmx")

  // a dummy wrapper to simplify expressions
  @Suppress("unused")
  fun id() = this
}