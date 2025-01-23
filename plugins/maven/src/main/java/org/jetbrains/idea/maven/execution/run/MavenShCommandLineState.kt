// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildView
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteConnectionCreator
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.project.MavenHomeType
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.MavenServerEmbedder
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Function

class MavenShCommandLineState(environment: ExecutionEnvironment, private val myConfiguration: MavenRunConfiguration) : CommandLineState(
  environment), RemoteConnectionCreator {
  private val myRemoteConnectionCreator: RemoteConnectionCreator? = null

  @Throws(ExecutionException::class)
  override fun startProcess(): ProcessHandler {
    return runBlockingCancellable {
      val eelApi = myConfiguration.project.getEelDescriptor().upgrade()
      val processOptions = EelExecApi.ExecuteProcessOptions.Builder(if (SystemInfo.isWindows) "cmd.exe" else "/bin/sh")
        .env(getEnv(eelApi.exec.fetchLoginShellEnvVariables()))
        .workingDirectory(EelPath.parse(myConfiguration.runnerParameters.workingDirPath.toString(), eelApi.descriptor))
        .args(getArgs(eelApi))
        .build()

      val result = eelApi.exec.execute(processOptions)
      return@runBlockingCancellable when (result) {
        is EelResult.Error -> {
          MavenLog.LOG.warn("Cannot execute maven goal: errcode: ${result.error.errno}, message:  ${result.error.message}")
          throw ExecutionException(result.error.message)
        }
        is EelResult.Ok -> {
          KillableColoredProcessHandler.Silent(result.value.convertToJavaProcess(), processOptions.toString(), Charsets.UTF_8, emptySet())
        }

      }
    }
  }

  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
    val taskId =
      ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myConfiguration.getProject())
    val descriptor =
      DefaultBuildDescriptor(taskId, myConfiguration.name, myConfiguration.runnerParameters.workingDirPath, System.currentTimeMillis())
    val processHandler = startProcess()

    if (MavenRunConfigurationType.isDelegate(environment)) {
      return doDelegateBuildExecute(executor, taskId, descriptor, processHandler)
    }
    else {
      return doRunExecute(executor, taskId, descriptor, processHandler)
    }
  }

  @Throws(ExecutionException::class)
  fun doRunExecute(
    executor: Executor,
    taskId: ExternalSystemTaskId,
    descriptor: DefaultBuildDescriptor,
    processHandler: ProcessHandler,
  ): ExecutionResult {
    val buildView: BuildView? = createBuildView(executor, descriptor)

    if (buildView == null) {
      MavenLog.LOG.warn("buildView is null for " + myConfiguration.getName())
    }
    val eventProcessor =
      MavenBuildEventProcessor(myConfiguration, buildView!!, descriptor, taskId, { it }, Function { ctx: MavenParsingContext? -> StartBuildEventImpl(descriptor, "") })

    processHandler.addProcessListener(BuildToolConsoleProcessAdapter(eventProcessor))
    buildView.attachToProcess(MavenHandlerFilterSpyWrapper(processHandler))

    return DefaultExecutionResult(buildView, processHandler);
  }

  @Throws(ExecutionException::class)
  private fun createBuildView(
    executor: Executor,
    descriptor: BuildDescriptor,
  ): BuildView? {
    val console = createConsole(executor) ?: return null
    val project = myConfiguration.getProject()
    val viewManager = project.getService<ExternalSystemRunConfigurationViewManager?>(ExternalSystemRunConfigurationViewManager::class.java)
    return object : BuildView(project, console, descriptor, "build.toolwindow.run.selection.state", viewManager) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        super.onEvent(buildId, event)
        viewManager.onEvent(buildId, event)
      }
    }
  }


  @Throws(ExecutionException::class)
  fun doDelegateBuildExecute(
    executor: Executor,
    taskId: ExternalSystemTaskId,
    descriptor: DefaultBuildDescriptor,
    processHandler: ProcessHandler,
  ): ExecutionResult {
    val consoleView = createConsole(executor)
    val viewManager = environment.project.getService<BuildViewManager?>(BuildViewManager::class.java)
    descriptor.withProcessHandler(MavenBuildHandlerFilterSpyWrapper(processHandler), null)
    descriptor.withExecutionEnvironment(getEnvironment())
    val startBuildEvent = StartBuildEventImpl(descriptor, "")
    val eventProcessor =
      MavenBuildEventProcessor(myConfiguration, viewManager, descriptor, taskId,
                               { it }, { startBuildEvent })

    processHandler.addProcessListener(BuildToolConsoleProcessAdapter(eventProcessor))
    val res = DefaultExecutionResult(consoleView, processHandler, DefaultActionGroup())
    res.setRestartActions(JvmToggleAutoTestAction())
    return res
  }

  override fun createRemoteConnection(environment: ExecutionEnvironment?): RemoteConnection? {
    return null
  }

  override fun isPollConnection(): Boolean {
    return false
  }

  private fun getArgs(eelApi: EelApi): List<String> {
    val args = ArrayList<String>()
    if (eelApi.platform is EelPlatform.Windows) {
      args.add(getScriptPath(".cmd"))
    }
    else {
      args.add(getScriptPath(""))
    }
    addIdeaParameters(args, eelApi.platform is EelPlatform.Windows)
    args.addAll(myConfiguration.runnerParameters.options)
    args.addAll(myConfiguration.runnerParameters.goals)
    return args
  }

  private fun addIdeaParameters(args: ArrayList<String>, isWindows: Boolean) {
    args.add("-Didea.version=${MavenUtil.getIdeaVersionToPassToMavenProcess()}")
    val path = MavenServerManager.getInstance().getMavenEventListener().absolutePath
    val escapedPath =
      if (path.contains(' ')) {
        if (isWindows) "\"${path}\"" else path.replace(" ", "\\ ")
      }
      else {
        path
      }
    args.add("-D${MavenServerEmbedder.MAVEN_EXT_CLASS_PATH}=$escapedPath")


  }

  private fun getEnv(existingEnv: Map<String, String>): Map<String, String> {
    val map = HashMap<String, String>();
    map.putAll(existingEnv)
    myConfiguration.runnerSettings?.environmentProperties?.let { map.putAll(map) }
    val javaParams = myConfiguration.createJavaParameters(myConfiguration.project);
    map.put("JAVA_HOME", javaParams.jdkPath)
    return map
  }

  private fun getScriptPath(extension: String): String {
    val type: MavenHomeType = MavenProjectsManager.getInstance(myConfiguration.project).getGeneralSettings().getMavenHomeType()
    if (type is MavenWrapper) {
      return "mvnw$extension"
    }

    val distribution = MavenDistributionsCache.getInstance(myConfiguration.getProject())
      .getMavenDistribution(myConfiguration.runnerParameters.workingDirPath)
    return distribution.mavenHome.resolve("bin").resolve("mvn$extension").toString()
  }
}
