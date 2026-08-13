package com.intellij.ide.starter.runner

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.classFileVerification
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.monitoringDumpsIntervalSeconds
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDEStartConfig
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.asRemDevContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptions.Companion.TEST_SCRIPT_FILE_OPTION
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.process.collectJavaThreadDumpSuspendable
import com.intellij.ide.starter.process.collectMemoryDump
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.profiler.ProfilerInjector
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.screenRecorder.IDEScreenRecorder
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.ReportingPathUtils.checkPathLength
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.startProfileNativeThreads
import com.intellij.ide.starter.utils.stopProfileNativeThreads
import com.intellij.ide.starter.utils.takeScreenshot
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One run of an IDE: what to start it with, and what the launches of that run report.
 *
 * [copy] gives the copy reporting of its own — a fresh [IDEReportingDataRegistry], so a new [originalIdeReportingData], a method execution
 * index counted from one again and its own link on CI. That is what a copy standing for another IDE process wants; a copy meant to be the
 * same run would report itself twice, so copy only to start something.
 */
data class IDERunContext(
  val testContext: IDETestContext,
  val commandLine: (IDERunContext) -> IDECommandLine = ::openTestCaseProject,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val runTimeout: Duration = 10.minutes,
  val useStartupScript: Boolean = true,
  val verboseOutput: Boolean = false,
  val launchName: String = "",
  val expectedKill: Boolean = false,
  val expectedExitCode: Int = 0,
  val analyzeProcessExit: Boolean = true,
  val collectNativeThreads: Boolean = false,
  private val stdOut: ExecOutputRedirect? = null,
) {
  /**
   * What this run is called wherever the name has to stay the same across runs of the same test — the identity IJ Perf, bisect and the
   * screenshot service know it by — and, for the same reason, the path under which anything of this run is published.
   *
   * A published artifact is only worth publishing if the tools that link to it can name it, and all IJ Perf keeps of a run is the project and
   * the method name: a path it cannot rebuild out of those two is a path nothing ever navigates to again. Publishing under the launch's own
   * [IDEReportingData.artifactPath] instead takes IJ Perf's links, issue creation and log analysis with it, and buries the artifacts a few
   * directories deeper on the way out.
   *
   * It names the whole IDE process, so it is deliberately blind to the test methods that process reports for. To name a launch to a human,
   * use the launch's own [IDEReportingData.humanReadableTestName].
   */
  val contextName: String = (if (launchName.isNotBlank()) "${testContext.testName}/${launchName}" else testContext.testName)

  private val reportingDataRegistry = IDEReportingDataRegistry(testContext, launchName)

  fun registeredIdeReportingData(): List<IDEReportingData> = reportingDataRegistry.all()
  internal fun ideReportingDataFromCurrentToOldest(): List<IDEReportingData> = reportingDataRegistry.fromCurrentToOldest()

  val lastIdeReportingData: IDEReportingData get() = reportingDataRegistry.current
  val originalIdeReportingData: IDEReportingData = reportingDataRegistry.original

  /**
   * The reporting directories of the one launch this run had. A run that reported for several test methods has no single answer to give,
   * so these throw; reach for [lastIdeReportingData] or [registeredIdeReportingData] instead, which say which launch you mean.
   */
  val reportsDir: Path
    get() = registeredIdeReportingData().singleOrNull()?.reportsDir ?: multipleReportingDirsError("reportsDir")
  val snapshotsDir: Path
    get() = registeredIdeReportingData().singleOrNull()?.snapshotsDir ?: multipleReportingDirsError("snapshotsDir")
  val logsDir: Path
    get() = registeredIdeReportingData().singleOrNull()?.logsDir ?: multipleReportingDirsError("logsDir")

  private fun multipleReportingDirsError(accessor: String): Nothing =
    error("There have been several reporting dirs, so '$accessor' cannot tell which one it means. " +
          "You need either to choose the last one or perform your action for all reporting dirs.")

  private val patchesForVMOptions: ConcurrentList<VMOptions.() -> Unit> = ContainerUtil.createConcurrentList()

  fun registerNewIdeReportingData(actionToResetLogDir: (Path) -> Unit): IDEReportingData =
    reportingDataRegistry.register(actionToResetLogDir)

  fun publishArtifacts() {
    registeredIdeReportingData().forEach { it.publishArtifacts(testContext) }
    // the event log is written by the IDE process as a whole rather than per launch, so it goes under the first reporting data of the
    // process - under the same path scheme as the rest, so that it lands next to the logs and reports it belongs with
    catchAll("publish event-log-data") { originalIdeReportingData.publishArtifact(testContext, testContext.paths.eventLogDataDir, "event-log-data") }
  }

  @Suppress("unused")
  fun withVMOptions(patchVMOptions: VMOptions.() -> Unit): IDERunContext = addVMOptionsPatch(patchVMOptions)

  /**
   * Method applies a patch to the current run, and the patch will be disregarded for the next run.
   */
  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> Unit): IDERunContext {
    patchesForVMOptions.add(patchVMOptions)
    return this
  }

  private fun installProfiler(): IDERunContext {
    @Suppress("DEPRECATION")
    return when (val profilerType = testContext.profilerType) {
      ProfilerType.ASYNC_ON_START, ProfilerType.YOURKIT, ProfilerType.ASYNC -> {
        val profiler = di.direct.instance<ProfilerInjector>(tag = profilerType)
        logOutput("Injecting profiler ${profiler.type.kind}")
        profiler.injectProfiler(this)
      }
      ProfilerType.NONE -> {
        this.addVMOptionsPatch { removeProfilerAgents() }
        logOutput("No profiler is specified.")
        return this
      }
    }
  }

  fun calculateVmOptions(): VMOptions {
    return testContext.ide.vmOptions.copy().apply {
      setAdditionalRegistryKeysIfNeeded()
      disableStartupDialogs()
      disableNewUsersOnboardingDialogue()
      disableFreezeReportingProfiling()
      setFatalErrorNotificationEnabled()
      setFlagIntegrationTests()
      setJcefJsQueryPoolSize(10_000)
      if (!testContext.isRemDevContext()) {
        takeScreenshotsPeriodically()
      }
      withJvmCrashLogDirectory(lastIdeReportingData.logsDir.resolve("jvm-crash").createDirectories())
      withHeapDumpOnOutOfMemoryDirectory(lastIdeReportingData.logsDir.resolve("heap-dump").createDirectories())
      withGCLogs(lastIdeReportingData.reportsDir.resolve("gcLog.log"))
      setOpenTelemetryMaxFilesNumber()

      if (ConfigurationStorage.classFileVerification()) {
        withClassFileVerification()
      }

      if (ConfigurationStorage.includeRuntimeModuleRepositoryInIde()) {
        setRuntimeModuleRepository(testContext.ide.installationPath)
      }

      installProfiler()
      setSnapshotPath(lastIdeReportingData.snapshotsDir)
      collectOpenTelemetry()
      setupLogDir()

      patchesForVMOptions.forEach { patchVMOptions -> patchVMOptions() }

      if (!useStartupScript) {
        require(commands.count() > 0) { "script builder is not allowed when useStartupScript is disabled" }
      }
      // Allow an overridden script file, required for migration of Rider performance tests
      else if (!this.hasOption(TEST_SCRIPT_FILE_OPTION))
        installTestScript(testName = contextName, paths = testContext.paths, commands = commands)

      applyCustomCommandJvmArguments()
    }
  }

  private fun VMOptions.applyCustomCommandJvmArguments() {
    if (!testContext.ide.isFromSources) return
    val customCommand = commandLine(this@IDERunContext) as? IDECommandLine.CustomCommand ?: return
    val customCommandJvmArguments = requireNotNull(
      DevBuildServerRunner.instance.readCustomCommandJvmArguments(testContext.ide.installationPath, customCommand.command)
    ) {
      "No '${customCommand.command}' custom command in the product info of ${testContext.ide.installationPath}"
    }
    customCommandJvmArguments.forEach { addLine(it) }
  }

  fun runIDE(): IDEStartResult {
    return runBlocking { withContext(Dispatchers.IO) { runIdeSuspending() } }
  }

  suspend fun runIdeSuspending(): IDEStartResult {
    return di.direct.instance<IDEProcess>().run(this)
  }

  internal fun getStderr(): ExecOutputRedirect {
    val prefix = "[ide-${contextName}-err]"
    return if (stdOut != null) {
      ExecOutputRedirect.DelegatedWithPrefix(prefix, stdOut)
    }
    else {
      ExecOutputRedirect.ToStdOutAndTail(prefix)
    }
  }

  internal fun getStdout(): ExecOutputRedirect {
    if (stdOut != null) {
      return stdOut
    }
    return if (verboseOutput) ExecOutputRedirect.ToStdOut("[ide-${contextName}-out]") else ExecOutputRedirect.ToString()
  }


  internal fun getErrorMessage(t: Throwable, ciFailureDetails: String?): String? {
    val failureCauseFile = lastIdeReportingData.logsDir.resolve("failure_cause.txt")
    val errorMessage = if (Files.exists(failureCauseFile)) {
      Files.readString(failureCauseFile)
    }
    else {
      t.message ?: t.javaClass.name
    }
    return when {
      ciFailureDetails == null -> errorMessage
      errorMessage == null -> ciFailureDetails
      else -> "$ciFailureDetails\n$errorMessage"
    }
  }

  internal fun logDisabledPlugins(paths: IDEDataPaths) {
    val disabledPlugins = paths.configDir.resolve("disabled_plugins.txt")
    if (disabledPlugins.exists()) {
      logOutput("The list of disabled plugins: " + disabledPlugins.readText())
    }
  }

  internal suspend fun captureDiagnosticOnKill(
    logsDir: Path,
    jdkHome: Path,
    startConfig: IDEStartConfig,
    ideProcessId: Long,
    snapshotsDir: Path,
  ) {
    if (!calculateVmOptions().hasHeadlessMode() && testContext !is IDERemDevTestContext) {
      catchAll {
        takeScreenshot(logsDir)
      }
    }
    if (expectedKill) return

    if (collectNativeThreads) {
      val fileToStoreNativeThreads = logsDir.resolve("native-thread-dumps.txt")
      startProfileNativeThreads(ideProcessId.toString())
      delay(15.seconds)
      stopProfileNativeThreads(ideProcessId.toString(), fileToStoreNativeThreads.toAbsolutePath().toString())
    }
    val dumpFile = logsDir.resolve("threadDump-before-kill-${System.currentTimeMillis()}.txt")
    val memoryDumpFile = snapshotsDir.resolve("memoryDump-before-kill-${System.currentTimeMillis()}.hprof.gz")
    catchAll {
      collectJavaThreadDumpSuspendable(jdkHome, startConfig.workDir, ideProcessId, dumpFile)
    }
    catchAll {
      if (isLowMemorySignalPresent(logsDir)) {
        collectMemoryDump(jdkHome, startConfig.workDir, ideProcessId, memoryDumpFile)
      }
    }
  }

  private fun isLowMemorySignalPresent(logsDir: Path): Boolean {
    return logsDir.walk().single { it.name == "idea.log" }.bufferedReader().useLines { lines ->
      lines.any { line ->
        line.contains("Low memory signal received: afterGc=true")
      }
    }
  }

  suspend fun startCollectThreadDumpsLoop(
    process: IDEHandle,
    jdkHome: Path,
    workDir: Path,
    collectingProcessId: Long,
    processName: String,
  ) {
    var cnt = 0
    while (process.isAlive) {
      delay(ConfigurationStorage.monitoringDumpsIntervalSeconds().seconds)
      if (!process.isAlive) break

      val dumpFile = lastIdeReportingData.logsDir
        .resolve("monitoring-thread-dumps-${processName}")
        .resolve("threadDump-${++cnt}-${getCurrentTimestamp()}.txt")
      checkPathLength(dumpFile).parent.createDirectories()

      logOutput("Dumping threads to $dumpFile")
      catchAll { collectJavaThreadDumpSuspendable(jdkHome, workDir, collectingProcessId, dumpFile) }
    }
  }

  private fun getCurrentTimestamp(): String {
    val current = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
    return current.format(formatter)
  }


  internal fun logStartupInfo(finalOptions: VMOptions) {
    logOutput(buildString {
      appendLine("Starting IDE for $contextName with timeout $runTimeout")
      appendLine("  VM Options: [" + finalOptions.toString().lineSequence().map { it.trim() }.joinToString(" ") + "]")
      appendLine("  On Java : [" + System.getProperty("java.home") + "]")
    })
  }

  @OptIn(ExperimentalPathApi::class)
  internal fun deleteSavedAppStateOnMac() {
    if (SystemInfoRt.isMac) {
      val filesToBeDeleted = listOf(
        "com.jetbrains.${testContext.testCase.ideInfo.installerProductName}-EAP.savedState",
        "com.jetbrains.${testContext.testCase.ideInfo.installerProductName}.savedState"
      )
      val home = System.getProperty("user.home")
      val savedAppStateDir = Path.of(home).resolve("Library/Saved Application State")
      savedAppStateDir
        .listDirectoryEntriesQuietly()
        ?.filter { file -> filesToBeDeleted.any { fileToBeDeleted -> file.name == fileToBeDeleted } }
        ?.forEach { it.deleteRecursively() }
    }
  }

  private fun collectOpenTelemetry(): IDERunContext = addVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.file", lastIdeReportingData.logsDir.resolve(IDETestContext.OPENTELEMETRY_FILE))
  }

  private fun setupLogDir(): IDERunContext = addVMOptionsPatch {
    addSystemProperty("idea.log.path", lastIdeReportingData.logsDir)
  }

  fun withScreenRecording() {
    if (testContext.isRemDevContext() && testContext != testContext.asRemDevContext().frontendIDEContext && !calculateVmOptions().hasHeadlessMode()) {
      logOutput("Will not record screen for a backend of remote dev")
      return
    }
    val screenRecorder = IDEScreenRecorder.create(this)
    EventsBus.subscribeOnce(IDEScreenRecorder::class.java) { _: IdeLaunchEvent ->
      screenRecorder.start()
    }
    EventsBus.subscribeOnce(IDEScreenRecorder::class.java) { _: IdeAfterLaunchEvent ->
      screenRecorder.stop()
    }
  }
}
