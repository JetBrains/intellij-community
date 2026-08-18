// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteConnectionCreator
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import java.io.OutputStream
import java.nio.file.Path

class MavenSurefireConfigurationFactory(
  private val configurationName: String,
  private val alternativeJrePath: String?,
) : ConfigurationFactory(MavenRunConfigurationType.getInstance()) {

  override fun getId(): String = "MavenSurefire"

  override fun createTemplateConfiguration(project: Project): RunConfiguration =
    MavenSurefireRunConfiguration(project, this, configurationName, alternativeJrePath)

  override fun createConfiguration(name: String?, template: RunConfiguration): RunConfiguration =
    MavenSurefireRunConfiguration(template.project, this, configurationName, alternativeJrePath)
}

class MavenSurefireRunConfiguration(
  project: Project?,
  configurationFactory: ConfigurationFactory?,
  name: String,
  private val alternativeJrePath: String?,
) : MavenRunConfiguration(project, configurationFactory, name), SurefireRunConfiguration {

  /** Directory of the Maven module whose Surefire test results to display after the build. */
  var testModuleDirectory: String? = null

  override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
    val delegate = super.getState(executor, env) ?: return null
    val testDir = testModuleDirectory ?: return delegate
    return SurefireTestRunProfileState(delegate, this, executor, testDir)
  }

  override fun createRemoteConnectionCreator(javaParameters: JavaParameters): RemoteConnectionCreator {
    return object : RemoteConnectionCreator {
      override fun createRemoteConnection(environment: ExecutionEnvironment): RemoteConnection? {
        try {
          val parameters = JavaParameters()
          parameters.jdk = JavaParametersUtil.createProjectJdk(project, alternativeJrePath)
          // RemoteConnectionBuilder populates parameters.vmParametersList with the JDWP agent string.
          val connection = RemoteConnectionBuilder(false, DebuggerSettings.getInstance().transport, "")
            .asyncAgent(Registry.`is`("maven.use.scripts.debug.agent"))
            .project(environment.project)
            .matchWithExecutionTarget()
            .create(parameters)

          // patchVmParameters flips suspend=n,server=y -> suspend=y,server=y, so the test JVM waits for the debugger.
          val jdwpArgs = MavenExecutionEnvironmentProviderUtil.patchVmParameters(parameters.vmParametersList)
            .joinToString(" ")
          if (jdwpArgs.isNotEmpty()) {
            javaParameters.programParametersList.add("-Dmaven.surefire.debug=$jdwpArgs")
          }
          return connection
        }
        catch (e: ExecutionException) {
          throw RuntimeException("Cannot create debug connection", e)
        }
      }

      override fun isPollConnection(): Boolean = true
    }
  }
}

private class SurefireTestRunProfileState(
  private val delegate: RunProfileState,
  private val configuration: MavenSurefireRunConfiguration,
  private val executor: Executor,
  private val testModuleDirectory: String,
) : RunProfileState, RemoteConnectionCreator {

  // Delegate debug-connection creation to the inner Maven state so GenericDebuggerRunner can attach.
  // MavenShCommandLineState (script mode) and MavenCommandLineState both implement RemoteConnectionCreator.
  override fun createRemoteConnection(environment: ExecutionEnvironment): RemoteConnection? =
    (delegate as? RemoteConnectionCreator)?.createRemoteConnection(environment)

  override fun isPollConnection(): Boolean =
    (delegate as? RemoteConnectionCreator)?.isPollConnection() ?: true

  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
    // Run Maven and discard its BuildView console; we replace it with the SM test runner.
    val mavenResult = delegate.execute(executor, runner) ?: return null
    val mavenHandler = (mavenResult as? DefaultExecutionResult)?.processHandler ?: return mavenResult

    val proxy = SurefireProcessProxy(mavenHandler, testModuleDirectory)
    val properties = SurefireTestConsoleProperties(configuration, this.executor)
    val console = SMTestRunnerConnectionUtil.createConsole(properties)
    console.attachToProcess(proxy)

    val rerunAction = properties.createRerunFailedTestsAction(console)
    rerunAction.setModelProvider { console.resultsViewer }

    val result = DefaultExecutionResult(console, proxy)
    result.setRestartActions(rerunAction, JvmToggleAutoTestAction())
    return result
  }
}

/**
 * Wraps the Maven [ProcessHandler] to present a unified lifecycle to the SM test runner console.
 *
 * - `startNotify()` starts the proxy AND the Maven handler so output begins to flow.
 * - On Maven termination, surefire reports are parsed and fed as TC service messages before the
 *   proxy signals its own termination.  This keeps everything in a single Run toolwindow tab.
 * - `destroyProcess()` / `detachProcess()` delegate to the Maven handler so the Stop button works.
 */
private class SurefireProcessProxy(
  private val mavenHandler: ProcessHandler,
  private val testModuleDirectory: String,
) : ProcessHandler() {

  init {
    mavenHandler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        // Forward Maven build output (compilation, downloading, etc.) to the proxy so it
        // appears in the SM console.  Text arriving before testSuiteStarted is attributed
        // to the root "Test Results" node, which is exactly where the user expects it.
        notifyTextAvailable(event.text, outputType)
      }

      override fun processTerminated(event: ProcessEvent) {
        val messages = SurefireReportParser.collectMessages(Path.of(testModuleDirectory))
        for (message in messages) {
          notifyTextAvailable("$message\n", ProcessOutputTypes.STDOUT)
        }
        notifyProcessTerminated(event.exitCode)
      }
    })
  }

  override fun startNotify() {
    super.startNotify()           // fires startNotified on proxy listeners (SM console)
    mavenHandler.startNotify()    // starts event dispatching on the Maven process
  }

  override fun destroyProcessImpl() = mavenHandler.destroyProcess()
  override fun detachProcessImpl() = mavenHandler.detachProcess()
  override fun detachIsDefault(): Boolean = false
  override fun getProcessInput(): OutputStream? = null
}

/** Identifies a [MavenRunConfiguration] that runs tests through Maven Surefire. */
interface SurefireRunConfiguration
