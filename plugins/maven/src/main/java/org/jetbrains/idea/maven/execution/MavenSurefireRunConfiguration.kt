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
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.project.MavenProjectBundle
import java.nio.file.Path

private val LOG = logger<MavenSurefireRunConfiguration>()

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
    return SurefireTestRunProfileState(delegate, this, env, executor, testDir)
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
  private val env: ExecutionEnvironment,
  private val executor: Executor,
  private val testModuleDirectory: String,
) : RunProfileState {

  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
    val result = delegate.execute(executor, runner) ?: return null
    val processHandler = (result as? DefaultExecutionResult)?.processHandler
    processHandler?.addProcessListener(object : ProcessListener {
      override fun processTerminated(event: ProcessEvent) {
        val messages = SurefireReportParser.collectMessages(Path.of(testModuleDirectory))
        if (messages.isEmpty()) return
        ApplicationManager.getApplication().invokeLater {
          if (env.project.isDisposed) return@invokeLater
          showTestResults(messages)
        }
      }
    })
    return result
  }

  private fun showTestResults(messages: List<String>) {
    val nopHandler = NopProcessHandler()
    val properties = SurefireTestConsoleProperties(configuration, executor)
    val console = SMTestRunnerConnectionUtil.createConsole(properties)
    console.attachToProcess(nopHandler)
    nopHandler.startNotify()
    for (message in messages) {
      nopHandler.notifyTextAvailable("$message\n", ProcessOutputTypes.STDOUT)
    }
    nopHandler.destroyProcess()

    val descriptor = RunContentDescriptor(
      console, nopHandler, console.component,
      MavenProjectBundle.message("maven.surefire.test.results"),
      null, null,
      arrayOf(RerunAllAction()),
    )
    descriptor.executionId = env.executionId

    RunContentManager.getInstance(env.project).showRunContent(executor, descriptor)
  }

  private inner class RerunAllAction : AnAction(
    MavenProjectBundle.message("maven.surefire.rerun.all"),
    null,
    AllIcons.Actions.Rerun,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      try {
        val newEnv = ExecutionEnvironmentBuilder(env.project, executor)
          .runProfile(configuration)
          .runner(env.runner)
          .build()
        env.runner.execute(newEnv)
      }
      catch (ex: ExecutionException) {
        LOG.warn("Failed to rerun Surefire tests", ex)
      }
    }
  }
}

/** Identifies a [MavenRunConfiguration] that runs tests through Maven Surefire. */
interface SurefireRunConfiguration
