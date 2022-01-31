package com.intellij.ide.starter.runner

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.exec.ExecOutputRedirect
import com.intellij.ide.starter.exec.ExecTimeoutException
import com.intellij.ide.starter.exec.exec
import com.intellij.ide.starter.ide.CodeInjector
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.andThen
import com.intellij.ide.starter.process.collectJavaThreadDump
import com.intellij.ide.starter.process.destroyGradleDaemonProcessIfExists
import com.intellij.ide.starter.process.getJavaProcessId
import com.intellij.ide.starter.profiler.ProfilerInjector
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.*
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.measureTime

interface IDERunCloseContext {
  val wasRunSuccessful: Boolean
}

data class IDERunContext(
  val testContext: IDETestContext,
  val patchVMOptions: VMOptions.() -> VMOptions = { this },
  val commandLine: IDECommandLine? = null,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val codeBuilder: (CodeInjector.() -> Unit)? = null,
  val runTimeout: Duration = Duration.minutes(10),
  val traceStacksEvery: Duration = Duration.minutes(10),
  val useStartupScript: Boolean = true,
  val closeHandlers: List<IDERunCloseContext.() -> Unit> = listOf(),
  val verboseOutput: Boolean = false,
  val launchName: String = "",
  val expectedKill: Boolean = false
) {
  val contextName: String
    get() = if (launchName.isNotEmpty()) {
      "${testContext.testName}/$launchName"
    }
    else {
      testContext.testName
    }

  @Suppress("unused")
  fun verbose() = copy(verboseOutput = true)

  @Suppress("unused")
  fun withVMOptions(patchVMOptions: VMOptions.() -> VMOptions) = addVMOptionsPatch(patchVMOptions)

  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> VMOptions) = copy(
    patchVMOptions = this.patchVMOptions.andThen(patchVMOptions)
  )

  fun addCompletionHandler(handler: IDERunCloseContext.() -> Unit) = this.copy(closeHandlers = closeHandlers + handler)

  fun uploadProfileResultsToTeamCity(profilerSnapshotsDir: Path, artifactName: String) =
    this.addCompletionHandler {
      testContext.publishArtifact(source = profilerSnapshotsDir, artifactName = artifactName)
    }

  fun installProfiler(): IDERunContext {
    val profilerType = testContext.profilerType

    return when (profilerType) {
      ProfilerType.ASYNC, ProfilerType.YOURKIT -> {
        val profiler = di.direct.instance<ProfilerInjector>(tag = profilerType)
        logOutput("Injecting profiler ${profiler.type.kind}")
        profiler.injectProfiler(this)
      }
      ProfilerType.NONE -> {
        logOutput("No profiler is specified. Skipping profiler setup")
        return this
      }
    }
  }

  // TODO: refactor this
  private fun prepareToRunIDE(): IDEStartResult {
    val paths = testContext.paths
    val logsDir = paths.logsDir.createDirectories()
    val jvmCrashLogDirectory = logsDir.resolve("jvm-crash").createDirectories()
    val heapDumpOnOomDirectory = logsDir.resolve("heap-dump").createDirectories()
    val disabledPlugins = paths.configDir.resolve("disabled_plugins.txt")
    if (disabledPlugins.toFile().exists()) {
      logOutput("The list of disabled plugins: " + disabledPlugins.toFile().readText())
    }

    val stdout = if (verboseOutput) ExecOutputRedirect.ToStdOut("[ide-${contextName}-out]") else ExecOutputRedirect.ToString()
    val stderr = ExecOutputRedirect.ToStdOut("[ide-${contextName}-err]")

    var successfulRun = true
    val host by lazy { di.direct.instance<CodeInjector>() }

    try {
      val codeBuilder = codeBuilder
      if (codeBuilder != null) {
        host.codeBuilder()
      }

      val finalOptions = testContext.ide.originalVMOptions
        .disableStartupDialogs()
        .usingStartupFramework()
        .setFatalErrorNotificationEnabled()
        .setFlagIntegrationTests()
        .takeScreenshotIfFailure(logsDir)
        .withJvmCrashLogDirectory(jvmCrashLogDirectory)
        .withHeapDumpOnOutOfMemoryDirectory(heapDumpOnOomDirectory)
        .let { testContext.test.vmOptionsFix(it) }
        .let { testContext.patchVMOptions(it) }
        .patchVMOptions()
        .let {
          if (!useStartupScript) {
            require(commands.count() > 0) { "script builder is not allowed when useStartupScript is disabled" }
            it
          }
          else
            it.installTestScript(contextName, paths, commands)
        }

      if (codeBuilder != null) {
        host.setup(testContext)
      }

      testContext.setProviderMemoryOnlyOnLinux()

      val jdkHome: Path by lazy {
        try {
          testContext.ide.resolveAndDownloadTheSameJDK()
        }
        catch (e: Exception) {
          logError("Failed to download the same JDK as in ${testContext.ide.build}")
          logError(e.stackTraceToString())

          val defaultJavaHome = resolveInstalledJdk11()
          logOutput("JDK is not found in ${testContext.ide.build}. Fallback to default java: $defaultJavaHome")
          defaultJavaHome
        }
      }

      val startConfig = testContext.ide.startConfig(finalOptions, logsDir)
      if (startConfig is Closeable) {
        addCompletionHandler { startConfig.close() }
      }

      val commandLineArgs = when (val cmd = commandLine ?: IDECommandLine.OpenTestCaseProject) {
        is IDECommandLine.Args -> cmd.args
        is IDECommandLine.OpenTestCaseProject -> listOf(testContext.resolvedProjectHome.toAbsolutePath().toString())
      }

      val finalEnvVariables = startConfig.environmentVariables + finalOptions.env
      val extendedEnvVariablesWithJavaHome = finalEnvVariables.toMutableMap()
      extendedEnvVariablesWithJavaHome.putIfAbsent("JAVA_HOME", jdkHome.absolutePathString())
      val finalArgs = startConfig.commandLine + commandLineArgs
      logOutput(buildString {
        appendLine("Starting IDE for ${contextName} with timeout $runTimeout")
        appendLine("  Command line: [" + finalArgs.joinToString() + "]")
        appendLine("  VM Options: [" + finalOptions.toString().lineSequence().map { it.trim() }.joinToString(" ") + "]")
        appendLine("  On Java : [" + System.getProperty("java.home") + "]")
      })

      File(finalArgs.first()).setExecutable(true)

      val executionTime = measureTime {
        exec(
          presentablePurpose = "run-ide-$contextName",
          workDir = startConfig.workDir,
          environmentVariables = extendedEnvVariablesWithJavaHome,
          timeout = runTimeout,
          args = finalArgs,
          errorDiagnosticFiles = startConfig.errorDiagnosticFiles,
          stdoutRedirect = stdout,
          stderrRedirect = stderr,
          onProcessCreated = { process, pid ->
            val javaProcessId by lazy { getJavaProcessId(jdkHome, startConfig.workDir, pid, process) }
            thread(name = "jstack-${testContext.testName}", isDaemon = true) {
              var cnt = 0
              while (process.isAlive) {
                Thread.sleep(traceStacksEvery.inWholeMilliseconds)
                if (!process.isAlive) break

                val dumpFile = logsDir.resolve("threadDump-${++cnt}-${System.currentTimeMillis()}" + ".txt")
                logOutput("Dumping threads to $dumpFile")
                logOutput(Runtime.getRuntime().getRuntimeInfo())
                catchAll { collectJavaThreadDump(jdkHome, startConfig.workDir, javaProcessId, dumpFile, false) }
              }
            }
          },
          onBeforeKilled = { process, pid ->
            if (!expectedKill) {
              val javaProcessId by lazy { getJavaProcessId(jdkHome, startConfig.workDir, pid, process) }
              val dumpFile = logsDir.resolve("threadDump-before-kill-${System.currentTimeMillis()}" + ".txt")
              catchAll { collectJavaThreadDump(jdkHome, startConfig.workDir, javaProcessId, dumpFile) }
            }
            takeScreenshot(logsDir)
          }
        )
      }

      logOutput("IDE run $contextName completed in $executionTime")

      require(FileSystem.countFiles(paths.configDir) > 3) {
        "IDE must have created files under config directory at ${paths.configDir}. Were .vmoptions included correctly?"
      }

      require(FileSystem.countFiles(paths.systemDir) > 1) {
        "IDE must have created files under system directory at ${paths.systemDir}. Were .vmoptions included correctly?"
      }

      val vmOptionsDiff = startConfig.vmOptionsDiff()

      if (vmOptionsDiff != null && !vmOptionsDiff.isEmpty) {
        logOutput("VMOptions were changed:")
        logOutput("new lines:")
        vmOptionsDiff.newLines.forEach { logOutput("  $it") }
        logOutput("removed lines:")
        vmOptionsDiff.missingLines.forEach { logOutput("  $it") }
        logOutput()
      }

      return IDEStartResult(runContext = this, executionTime = executionTime, vmOptionsDiff = vmOptionsDiff, logsDir = logsDir)
    }
    catch (t: Throwable) {
      successfulRun = false
      if (t is ExecTimeoutException && !expectedKill) {
        error("Timeout of IDE run $contextName for $runTimeout")
      }
      else {
        logOutput("IDE run for $contextName has been expected to be killed after $runTimeout")
      }

      val failureCauseFile = testContext.paths.logsDir.resolve("failure_cause.txt")
      val errorMessage = if (Files.exists(failureCauseFile)) {
        Files.readString(failureCauseFile)
      }
      else {
        t.message ?: t.javaClass.name
      }
      if (!expectedKill) {
        throw Exception(errorMessage, t)
      }
      else {
        return IDEStartResult(runContext = this, executionTime = runTimeout, logsDir = logsDir)
      }
    }
    finally {

      if (SystemInfo.isWindows) {
        destroyGradleDaemonProcessIfExists()
      }

      listOf(heapDumpOnOomDirectory, jvmCrashLogDirectory).filter { dir ->
        dir.listDirectoryEntries().isEmpty()
      }.forEach { it.toFile().deleteRecursively() }

      ErrorReporter.reportErrorsAsFailedTests(logsDir / "script-errors", contextName)
      val (artifactPath, artifactName) = if (successfulRun) contextName to "logs" else "run/$contextName" to "crash"
      testContext.publishArtifact(logsDir, artifactPath, artifactName)
      if (codeBuilder != null) {
        host.tearDown(testContext)
      }

      val closeContext = object : IDERunCloseContext {
        override val wasRunSuccessful: Boolean = successfulRun
      }

      closeHandlers.forEach {
        try {
          it.invoke(closeContext)
        }
        catch (t: Throwable) {
          logOutput("Failed to complete close step. ${t.message}.\n" + t)
          t.printStackTrace(System.err)
        }
      }
    }
  }

  fun runIDE(): IDEStartResult {
    return installProfiler()
      .prepareToRunIDE()
  }
}
