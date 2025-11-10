package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.FileSystem.cleanPathFromSlashes
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import com.intellij.tools.ide.util.common.logOutput
import org.jetbrains.annotations.ApiStatus.Experimental
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration

data class VMOptions(
  private val ide: InstalledIde,
  private var data: List<String>,
  private var env: Map<String, String>,
) {
  companion object {
    const val ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION: String = "full.scanning.on.startup.can.be.skipped"

    fun readIdeVMOptions(ide: InstalledIde, file: Path): VMOptions {
      return VMOptions(
        ide = ide,
        data = file.readLines().map { it.trim() }.filter { it.isNotBlank() },
        env = emptyMap(),
      )
    }
  }

  fun data(): List<String> = data.toList()

  override fun toString(): String = buildString {
    appendLine("VMOptions{")
    appendLine("  env=$env")
    for (line in data) {
      appendLine("  $line")
    }
    appendLine("} // VMOptions")
  }

  val environmentVariables: Map<String, String>
    get() = env

  fun addSystemProperty(key: String, value: Boolean): Unit = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Int): Unit = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Long): Unit = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Path): Unit = addSystemProperty(key, value.toAbsolutePath().toString())

  /**
   * This method adds a property to the IDE VM options. If the property already exists, it will be replaced by a new one.
   */
  fun addSystemProperty(key: String, value: String) {
    logOutput("Setting IDE system property: [${key}=${value}]")
    addLine(line = "-D${key}=${value}", filterPrefix = "-D${key}=")
  }

  /**
   * This method updates a property in the IDE VM options with a new value (old value and new value separated by a comma).
   * If the property does not exist, the property with a given value will be added.
   */
  private fun addSystemPropertyValue(key: String, value: String) {
    if (data.filter { it.contains("-D${key}") }.size == 1) {
      val oldLine = data.filter { it.startsWith("-D${key}") }[0]
      val oldValue = oldLine.split("=")[1]
      val updatedValue = "${oldValue},${value}"
      logOutput("Updating system property: [${key}=${updatedValue}]")
      addSystemProperty(key, updatedValue)
    }
    else {
      addSystemProperty(key, value)
    }
  }

  fun removeSystemProperty(key: String, value: Boolean) {
    logOutput("Removing system property: [${key}=${value}]")

    // FIXME this is a side effect that is not negated by addSystemProperty
    System.clearProperty(key) // to synchronize behavior in IDEA and on the test runner side

    removeLine(line = "-D${key}=${value}")
  }

  fun clearSystemProperty(key: String) {
    data = data.filterNot {
      it.startsWith("-D${key}=")
        .also { match -> if (match) logOutput("Removing system property: ${it.removePrefix("-D")}") }
    }
  }

  fun addLine(line: String, filterPrefix: String? = null) {
    if (data.contains(line)) return
    data = (if (filterPrefix == null) data else data.filterNot { it.trim().startsWith(filterPrefix) }) + line
  }

  fun removeLine(line: String) {
    if (!data.contains(line)) return
    data -= line
  }

  fun removeProfilerAgents() {
    removeAsyncAgent()
    removeYourkitAgent()
  }

  @Suppress("SpellCheckingInspection")
  fun removeAsyncAgent() {
    data = data.filterNot { it.contains("-agentpath:") && it.contains("async/libasyncProfiler") }
  }

  fun removeYourkitAgent() {
    data = data.filterNot { it.contains("-agentpath:") && it.contains("yourkit/bin/libyjpagent") }
  }

  private fun filterKeys(toRemove: (String) -> Boolean) {
    data = data.filterNot(toRemove)
  }

  fun setJavaHome(sdkObject: SdkObject): VMOptions = apply {
    withEnv("JAVA_HOME", sdkObject.sdkPath.toString())
  }

  fun withEnv(key: String, value: String) {
    env += key to value
  }

  fun writeIntelliJVmOptionFile(path: Path) {
    path.writeLines(data)
    logOutput("Write vmoptions patch to ${path}")
  }

  fun diffIntelliJVmOptionFile(theFile: Path): VMOptionsDiff {
    val loadedOptions = readIdeVMOptions(this.ide, theFile).data
    return VMOptionsDiff(originalLines = this.data, actualLines = loadedOptions)
  }

  fun writeJavaArgsFile(theFile: Path): Unit = JvmUtils.writeJvmArgsFile(theFile, this.data)

  fun overrideDirectories(paths: IDEDataPaths) {
    addSystemProperty(PathManager.PROPERTY_CONFIG_PATH, paths.configDir)
    addSystemProperty(PathManager.PROPERTY_SYSTEM_PATH, paths.systemDir)
    addSystemProperty(PathManager.PROPERTY_PLUGINS_PATH, paths.pluginsDir)
  }

  fun enableStartupPerformanceLog(perf: IDEStartupReports) = addSystemProperty("idea.log.perf.stats.file", perf.statsJSON)

  fun enableClassLoadingReport(filePath: Path) {
    addSystemProperty("idea.log.class.list.file", filePath)
    addSystemProperty("idea.record.classpath.info", "true")
  }

  fun enableVmTraceClassLoadingReport(filePath: Path) {
    if (!VMTrace.isSupported) return

    val vmTraceFile = VMTrace.vmTraceFile
    @Suppress("SpellCheckingInspection")
    addSystemProperty("idea.log.vmtrace.file", filePath)
    addLine("-agentpath:${vmTraceFile.toAbsolutePath()}=${filePath.toAbsolutePath()}")
  }

  fun enableExitMetrics(filePath: Path): Unit = addSystemProperty("idea.log.exit.metrics.file", filePath)

  fun enableVerboseOpenTelemetry(): Unit = addSystemProperty("idea.diagnostic.opentelemetry.verbose", true)

  fun allowSkippingFullScanning(): Unit = addSystemProperty(ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION, true)

  /**
   * [categories] - Could be packages, classes ...
   */
  fun configureLoggers(logLevel: LogLevel, vararg categories: String) {
    if (categories.isNotEmpty()) {
      val logLevelName = logLevel.name.lowercase()
      addSystemPropertyValue("idea.log.${logLevelName}.categories", categories.joinToString(separator = ",") {
        "#" + it.removePrefix("#")
      })
    }
  }

  fun configureLoggers(logLevel: String, vararg categories: String) {
    configureLoggers(LogLevel.valueOf(logLevel), *categories)
  }

  fun dropDebug() {
    data = data.filterNot { it.matches("-agentlib:jdwp=transport=dt_socket,server=y,suspend=.,address=.*".toRegex()) }
  }

  fun debug(port: Int = 5005, suspend: Boolean = true) {
    dropDebug()
    val suspendKey = if (suspend) "y" else "n"
    val configLine = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspendKey},address=*:${port}"
    addLine(configLine, filterPrefix = "-agentlib:jdwp")
  }

  /**
   * You should also use [com.intellij.ide.starter.ide.IDETestContext.setProfiler] method.
   * Example:
   * ```
   * context
   *   .setProfiler()
   *   .applyVMOptionsPatch { profileBuildToolDaemon() }
   * ```
   */
  fun profileBuildToolDaemon() {
    addSystemProperty("test.build_tool.daemon.profiler", true)
  }

  fun collectingGradleDaemonThreadDump() {
    addSystemProperty("test.build_tool.daemon.threads_dump", true)
  }

  fun inHeadlessMode(): Unit = addSystemProperty("java.awt.headless", true)
  fun hasHeadlessMode(): Boolean = data.any { it.contains("-Djava.awt.headless=true") }

  fun disableStartupDialogs() {
    addSystemProperty("jb.consents.confirmation.enabled", false)
    addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
    addSystemProperty("jb.privacy.policy.ai.assistant.text", "<!--999.999-->")
    addSystemProperty("marketplace.eula.reviewed.and.accepted", true)
    addSystemProperty("writerside.eula.reviewed.and.accepted", true)
  }

  fun disableNewUsersOnboardingDialogue() {
    addSystemProperty("ide.newUsersOnboarding", false)
  }

  fun setFreezeReportingInterval(interval: Duration) {
    addSystemProperty("performance.watcher.unresponsive.interval.ms", interval.inWholeMilliseconds)
  }

  fun disableFreezeReportingProfiling() {
    addSystemProperty("freeze.reporter.profiling", false)
  }

  fun takeScreenshotsPeriodically() {
    addSystemProperty("ide.performance.screenshot", "heartbeat")
  }

  fun setAdditionalRegistryKeysIfNeeded() {
    val additionalRegistryKeys = System.getProperty("additional.registry.keys")
    if (!additionalRegistryKeys.isNullOrEmpty()) {
      additionalRegistryKeys.split(";").forEach { keyValue ->
        val key = keyValue.split("=").first()
        val value = keyValue.split("=").last()
        logOutput("Setting additional registry key: [${key}=${value}]")
        addSystemProperty(key, value)
      }
    }
  }

  fun skipRefactoringDialogs() {
    addSystemProperty("ide.performance.skip.refactoring.dialogs", "true")
  }

  fun setJcefJsQueryPoolSize(size: Int) {
    addSystemProperty("ide.browser.jcef.jsQueryPoolSize", "$size")
  }

  fun installTestScript(testName: String, paths: IDEDataPaths, commands: Iterable<MarshallableCommand>) {
    val scriptText = commands.joinToString(separator = System.lineSeparator()) { it.storeToString() }
    installTestScript(testName, paths, scriptText)
  }

  fun installTestScript(testName: String, paths: IDEDataPaths, scriptText: String) {
    val scriptFileName = testName.cleanPathFromSlashes(replaceWith = "_") + ".text"
    val scriptFile = paths.systemDir.resolve(scriptFileName).apply {
      parent.createDirectories()
    }
    scriptFile.writeText(scriptText)

    logOutput("Test commands to be executed: ${System.lineSeparator()}$scriptText")

    addSystemProperty("testscript.filename", scriptFile)
    // Use non-success status code 1 when running IDE as a command line tool.
    addSystemProperty("testscript.must.exist.process.with.non.success.code.on.ide.error", "true")
  }

  fun setFlagIntegrationTests(): Unit = addSystemProperty("idea.is.integration.test", true)

  fun setIdeStartupDialogEnabled(value: Boolean = true): Unit = addSystemProperty("intellij.startup.wizard", value)

  fun setNeverShowInitConfigModal(): Unit = addSystemProperty("idea.initially.ask.config", "never")

  fun setFatalErrorNotificationEnabled(): Unit = addSystemProperty("idea.fatal.error.notification", true)

  fun setSnapshotPath(snapshotsDir: Path) {
    addSystemProperty("snapshots.path", snapshotsDir)
  }

  fun withJvmCrashLogDirectory(directory: Path) {
    addLine("-XX:ErrorFile=${directory.toAbsolutePath().resolve("java_error_in_idea_%p.log")}", "-XX:ErrorFile=")
  }

  fun withHeapDumpOnOutOfMemoryDirectory(directory: Path) {
    addLine("-XX:HeapDumpPath=${directory.toAbsolutePath().resolve("heap-dump.hprof")}", "-XX:HeapDumpPath=")
  }

  fun withXmx(sizeMb: Int): Unit = addLine("-Xmx" + sizeMb + "m", "-Xmx")

  fun withActiveProcessorCount(count: Int): Unit = addLine("-XX:ActiveProcessorCount=$count", "-XX:ActiveProcessorCount")

  fun withClassFileVerification() {
    addLine("-XX:+UnlockDiagnosticVMOptions")
    addLine("-XX:+BytecodeVerificationLocal")
  }

  fun withG1GC() {
    filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    filterKeys { it == "-XX:+UseG1GC" }
    addLine("-XX:+UseG1GC")
  }

  @Suppress("SpellCheckingInspection")
  fun withGCLogs(gcLogFile: Path): Unit = addLine("-Xlog:gc*:file=${gcLogFile.toAbsolutePath()}")

  /** see [JEP 318](https://openjdk.org/jeps/318) **/
  fun withEpsilonGC() {
    filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    filterKeys { it == "-XX:+UseG1GC" }
    addLine("-XX:+UnlockExperimentalVMOptions")
    addLine("-XX:+UseEpsilonGC")
    addLine("-Xmx16g", "-Xmx")
  }

  // VM-specific options

  @Suppress("unused")
  fun withJitCompilationLog() {
    addLine("-XX:+UnlockDiagnosticVMOptions")
    addLine("-XX:+LogCompilation")
  }

  @Suppress("unused")
  fun withTieredCompilation() {
    addLine("-XX:+TieredCompilation")
  }

  @Suppress("unused")
  fun withNoTieredCompilation() {
    addLine("-XX:-TieredCompilation")
  }

  @Suppress("unused")
  fun withCICompilerCount(count: Int) {
    removeLine("-XX:CICompilerCount=2")
    addLine("-XX:CICompilerCount=$count")
  }

  @Suppress("unused")
  fun withTier0ProfilingStartPercentage(percentage: Int) {
    addLine("-XX:Tier0ProfilingStartPercentage=$percentage")
  }

  /**
   * Only for specific JRE builds. The name is "/foo/bar/Baz method".
   */
  @Experimental
  @Suppress("unused")
  fun withC1OnlyBeforeCall(name: String) {
    addLine("-XX:C1OnlyBeforeCall=${name}")
  }

  @Suppress("unused")
  fun withCompilationLogs() {
    addLine("-XX:+UnlockDiagnosticVMOptions")
    addLine("-XX:+LogCompilation")
  }

  /**
   * One file will be produced each minute (depends on the configuration in OpenTelemetry).
   * Thus, by default, it's better to set it to a high number, so long-running tests will not report invalid metrics.
   */
  fun setOpenTelemetryMaxFilesNumber(maxFilesNumber: Int = 120) {
    addSystemProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", maxFilesNumber)
  }

  fun disableAutoImport(disabled: Boolean = true): Unit = addSystemProperty("external.system.auto.import.disabled", disabled)

  fun disableLoadShellEnv(disabled: Boolean = true): Unit = addSystemProperty("ij.load.shell.env", !disabled)

  fun executeRightAfterIdeOpened(executeRightAfterIdeOpened: Boolean = true) {
    addSystemProperty("performance.execute.script.right.after.ide.opened", executeRightAfterIdeOpened)
  }

  fun skipIndicesInitialization(value: Boolean = true): Unit = addSystemProperty("idea.skip.indices.initialization", value)

  fun doNotDisablePaidPluginsOnStartup(): Unit = addSystemProperty("ide.do.not.disable.paid.plugins.on.startup", true)

  fun doRefreshAfterJpsLibraryDownloaded(value: Boolean = true): Unit = addSystemProperty("idea.do.refresh.after.jps.library.downloaded", value)

  /**
   * Include [runtime module repository](psi_element://com.intellij.platform.runtime.repository) in the installed IDE.
   * Works only when the IDE is built from sources.
   */
  fun setRuntimeModuleRepository(installationDirectory: Path) {
    addSystemProperty("intellij.platform.runtime.repository.path", installationDirectory.resolve("modules/module-descriptors.jar").pathString)
  }

  fun hasOption(option: String): Boolean = data.any { it.contains(option) }

  fun getOptionValue(option: String): String {
    data.forEach { line ->
      if (line.contains(option)) {
        return line.replace("-D$option=", "")
      }
    }
    error("There is no such option")
  }

  fun isUnderDebug(): Boolean = ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:jdwp") }

  fun enforceSplash() {
    addLine("-Dsplash=true")
    addLine("-Didea.show.splash.longer=true")
  }

  @Suppress("SpellCheckingInspection")
  fun enforceNoSplash(): Unit = addLine("-Dnosplash=true")

  fun removeSystemClassLoader() {
    removeLine("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
  }

  fun addArchiveClassesAtExitIfNecessary() {
    val line = data.firstOrNull { it.startsWith("-XX:SharedArchiveFile=") } ?: return
    removeLine(line)
    dropDebug()
    addLine(line.replace("SharedArchiveFile", "ArchiveClassesAtExit"))
  }

  @Suppress("unused")
  fun setLockingMode(mode: Int) {
    addLine("-XX:+UnlockExperimentalVMOptions")
    addLine("-XX:LockingMode=$mode")
  }

  fun addSharedArchiveFile(pathToArchive: Path) {
    addLine("-XX:SharedArchiveFile=${pathToArchive}")
  }

  fun disableGotItTooltips() {
    addSystemProperty("ide.integration.test.disable.got.it.tooltips", true)
  }

  fun disableNativeFileChooser() {
    addSystemProperty("ide.mac.file.chooser.native", false)
    addSystemProperty("ide.win.file.chooser.native", false)
  }
}
