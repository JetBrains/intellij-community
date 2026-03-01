package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.process.ProcessInfo.Companion.toProcessInfo
import com.intellij.ide.starter.process.exec.ExecTimeoutException
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getIdeProcessIdWithRetry
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.report.TimeoutAnalyzer
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeBeforeKillEvent
import com.intellij.ide.starter.runner.events.IdeBeforeLaunchEvent
import com.intellij.ide.starter.runner.events.IdeBeforeRunIdeProcessEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.runner.events.StopProfilerEvent
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.openapi.util.io.NioFiles
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.Closeable
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.measureTime

class LocalIDEProcess : IDEProcess {
  override suspend fun run(runContext: IDERunContext): IDEStartResult {
    with(runContext) {
      EventsBus.postAndWaitProcessing(IdeBeforeLaunchEvent(this))

      deleteSavedAppStateOnMac()
      val paths = testContext.paths

      val stdout = getStdout()
      val stderr = getStderr()
      var ideProcessId = 0L
      var isRunSuccessful = true
      val ciFailureDetails = FailureDetailsOnCI.instance.getLinkToCIArtifacts(this)?.let { "Link on CI artifacts ${it}" }

      try {
        testContext.setProviderMemoryOnlyOnLinux()
        @Suppress("SSBasedInspection")
        val jdkHome = runBlocking(Dispatchers.Default) {
          resolveAndDownloadSameJDK()
        }

        val vmOptions: VMOptions = calculateVmOptions()
        val startConfig = testContext.ide.startConfig(vmOptions, logsDir)
        if (startConfig is Closeable) {
          EventsBus.subscribeOnce(this) { event: IdeAfterLaunchEvent ->
            if (event.runContext === this) {
              startConfig.close()
            }
          }
        }

        val mergedEnvVariables = (startConfig.environmentVariables + vmOptions.environmentVariables).toMutableMap()

        logDisabledPlugins(paths)
        logStartupInfo(vmOptions)

        val finalArgs = startConfig.commandLine + commandLine(this).args
        Path.of(finalArgs.first()).takeIf { it.isAbsolute }?.let {
          NioFiles.setExecutable(it)
        }
        val span = TestTelemetryService.spanBuilder("ide process").startSpan()
        EventsBus.postAndWaitProcessing(IdeBeforeRunIdeProcessEvent(runContext = this))
        val processPresentableName = "run-ide-$contextName"
        val executionTime = measureTime {
          ProcessExecutor(
            presentableName = processPresentableName,
            workDir = startConfig.workDir,
            environmentVariables = mergedEnvVariables,
            timeout = runTimeout,
            args = finalArgs,
            errorDiagnosticFiles = startConfig.errorDiagnosticFiles,
            stdoutRedirect = stdout,
            stderrRedirect = stderr,
            onProcessCreated = { process, pid ->
              span.addEvent("process created")
              runInterruptible {
                EventsBus.postAndWaitProcessing(IdeLaunchEvent(runContext = this, ideProcess = IDEProcessHandle(process.toHandle())))
              }
              ideProcessId = getIdeProcessIdWithRetry(process.toProcessInfo(), runContext)
              startCollectThreadDumpsLoop(logsDir, IDEProcessHandle(process.toHandle()), jdkHome, startConfig.workDir, ideProcessId, "ide")
            },
            onBeforeKilled = { process, pid ->
              span.end()
              computeWithSpan("runIde post-processing before killed") {
                logOutput("BeforeKilled: $processPresentableName")
                captureDiagnosticOnKill(logsDir, jdkHome, startConfig, process, snapshotsDir, runContext = this)
                EventsBus.postAndWaitProcessing(IdeBeforeKillEvent(this, process, pid))
                if (testContext.profilerType != ProfilerType.NONE) {
                  EventsBus.postAndWaitProcessing(StopProfilerEvent(listOf()))
                }
              }
            },
            expectedExitCode = expectedExitCode,
          ).startCancellable()
        }
        span.end()
        logOutput("IDE run $contextName completed in $executionTime")

        return IDEStartResult(runContext = this, executionTime = executionTime, vmOptionsDiff = startConfig.vmOptionsDiff())
      }
      catch (_: ExecTimeoutException) {
        if (expectedKill) {
          logOutput("IDE run for $contextName has been expected to be killed after $runTimeout")
          return IDEStartResult(runContext = this, executionTime = runTimeout)
        }
        else {
          isRunSuccessful = false

          val error = TimeoutAnalyzer.analyzeTimeout(this)
          if (error != null) {
            throw ExecTimeoutException(
              error.messageText + System.lineSeparator() +
              error.stackTraceContent + System.lineSeparator() +
              (ciFailureDetails ?: ""))
          }
          else {
            throw ExecTimeoutException("Timeout of IDE run '$contextName' for $runTimeout" + System.lineSeparator() + (ciFailureDetails
                                                                                                                       ?: ""))
          }
        }
      }
      catch (ce: CancellationException) {
        isRunSuccessful = false
        logOutput("Local ide process was cancelled", ce)
        throw ce
      }
      catch (exception: Throwable) {
        isRunSuccessful = false
        throw Exception(getErrorMessage(exception, ciFailureDetails), exception)
      }
      finally {
        try {
          computeWithSpan("runIde post-processing") {
            EventsBus.postAndWaitProcessing(IdeAfterLaunchEvent(runContext = this, isRunSuccessful = isRunSuccessful))

            if (isRunSuccessful) {
              validateVMOptionsWereSet(this)
            }
            testContext.collectJBRDiagnosticFiles(ideProcessId)

            val link = FailureDetailsOnCI.instance.getLinkToCIArtifacts(this)
            TeamCityCIServer.addTestMetadata(testName = null, TeamCityCIServer.TeamCityMetadataType.LINK, flowId = null, name = "Link to Logs and artifacts", value = link.toString())
            (CIServer.instance as? TeamCityCIServer)?.buildId?.let {
              TeamCityCIServer.addTestMetadata(testName = null, TeamCityCIServer.TeamCityMetadataType.LINK, flowId = null, name = "Start bisect", value = "https://ij-perf.labs.jb.gg/bisect/launcher?buildId=${it}")
            }
            ErrorReporter.instance.reportErrorsAsFailedTests(this)
          }
        }
        catch (e: Exception) {
          logError("Fail to execute finally block of runIDE $contextName", e)
          throw e
        }
        finally {
          computeWithSpan("runIde post-processing and artifacts publishing") {
            kotlin.runCatching {
              publishArtifacts()
            }.onFailure {
              logError("Fail to execute publishArtifacts run for $contextName", it)
            }.onSuccess {
              logOutput("Successfully finished publishArtifacts run for $contextName")
            }
          }
        }
      }
    }
  }
}