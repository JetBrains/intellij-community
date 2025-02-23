// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildView
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferContentsIfNonLocal
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.util.text.nullize
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor
import org.jetbrains.idea.maven.execution.*
import org.jetbrains.idea.maven.execution.MavenExternalParameters.encodeProfiles
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.DaemonedMavenDistribution
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.MavenServerEmbedder
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Function
import kotlin.io.path.Path

class MavenShCommandLineState(val environment: ExecutionEnvironment, private val myConfiguration: MavenRunConfiguration) : RunProfileState, RemoteState, PatchedRunnableState {
  private var myRemoteConnection: MavenRemoteConnection? = null

  @Throws(ExecutionException::class)

  private fun startProcess(debug: Boolean): ProcessHandler {
    return runWithModalProgressBlocking(myConfiguration.project, RunnerBundle.message("maven.target.run.label")) {
      val eelApi = myConfiguration.project.getEelDescriptor().upgrade()

      val mavenDistribution = MavenDistributionsCache.getInstance(myConfiguration.project).getMavenDistribution(myConfiguration.runnerParameters.workingDirPath)

      val processOptions = EelExecApi.ExecuteProcessOptions.Builder(if (isWindows()) "cmd.exe" else "/bin/sh")
        .env(getEnv(eelApi.exec.fetchLoginShellEnvVariables(), debug))
        .workingDirectory(Path(myConfiguration.runnerParameters.workingDirPath).asEelPath())
        .args(getArgs(eelApi)).let {
          if (!isWindows()) {
            it.ptyOrStdErrSettings(Pty(-1, -1, true))
          }
          else it
        }.build()

      val result = eelApi.exec.execute(processOptions)

      return@runWithModalProgressBlocking when (result) {
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
    try {
      val debug = runner is GenericDebuggerRunner
      val taskId =
        ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myConfiguration.getProject())
      val descriptor =
        DefaultBuildDescriptor(taskId, myConfiguration.name, myConfiguration.runnerParameters.workingDirPath, System.currentTimeMillis())
      val processHandler = startProcess(debug)

      if (MavenRunConfigurationType.isDelegate(environment)) {
        return doDelegateBuildExecute(executor, taskId, descriptor, processHandler)
      }
      else {
        return doRunExecute(taskId, descriptor, processHandler)
      }
    }
    catch (e: Exception) {
      MavenLog.LOG.error(e)
      throw e
    }
  }

  @Throws(ExecutionException::class)
  fun doRunExecute(
    taskId: ExternalSystemTaskId,
    descriptor: DefaultBuildDescriptor,
    processHandler: ProcessHandler,
  ): ExecutionResult {
    val buildView: BuildView? = createBuildView(descriptor)

    if (buildView == null) {
      MavenLog.LOG.warn("buildView is null for " + myConfiguration.getName())
    }
    val eventProcessor =
      MavenBuildEventProcessor(myConfiguration, buildView!!, descriptor, taskId, { it }, Function { ctx: MavenParsingContext? -> StartBuildEventImpl(descriptor, "") })

    processHandler.addProcessListener(BuildToolConsoleProcessAdapter(eventProcessor))
    buildView.attachToProcess(MavenHandlerFilterSpyWrapper(processHandler))

    return DefaultExecutionResult(buildView, processHandler)
  }

  @Throws(ExecutionException::class)
  private fun createBuildView(
    descriptor: BuildDescriptor,
  ): BuildView? {
    val console = createConsole()
    val project = myConfiguration.project
    val viewManager = project.getService<ExternalSystemRunConfigurationViewManager?>(ExternalSystemRunConfigurationViewManager::class.java)
    return object : BuildView(project, console, descriptor, "build.toolwindow.run.selection.state", viewManager) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        super.onEvent(buildId, event)
        viewManager.onEvent(buildId, event)
      }
    }
  }

  private fun createConsole(): ConsoleView {
    val searchScope = ExecutionSearchScopes.executionScope(environment.project, environment.runProfile)
    return TextConsoleBuilderFactory.getInstance().createBuilder(environment.project, searchScope).console
  }


  @Throws(ExecutionException::class)
  fun doDelegateBuildExecute(
    executor: Executor,
    taskId: ExternalSystemTaskId,
    descriptor: DefaultBuildDescriptor,
    processHandler: ProcessHandler,
  ): ExecutionResult {
    val consoleView = createConsole()
    val viewManager = environment.project.getService<BuildViewManager?>(BuildViewManager::class.java)
    descriptor.withProcessHandler(MavenBuildHandlerFilterSpyWrapper(processHandler), null)
    descriptor.withExecutionEnvironment(environment)
    val startBuildEvent = StartBuildEventImpl(descriptor, "")
    val eventProcessor =
      MavenBuildEventProcessor(myConfiguration, viewManager, descriptor, taskId,
                               { it }, { startBuildEvent })

    processHandler.addProcessListener(BuildToolConsoleProcessAdapter(eventProcessor))
    val res = DefaultExecutionResult(consoleView, processHandler, DefaultActionGroup())
    res.setRestartActions(JvmToggleAutoTestAction())
    return res
  }


  private fun getArgs(eel: EelApi): List<String> {
    val args = ParametersList()
    args.add(getScriptPath(eel))
    addIdeaParameters(args, eel)
    addSettingParameters(args)
    args.addAll(myConfiguration.runnerParameters.options)
    args.addAll(myConfiguration.runnerParameters.goals)
    if (isWindows()) {
      return listOf("/c", args.parametersString)
    }
    return args.list
  }

  private fun addSettingParameters(args: ParametersList) {
    val encodeProfiles = encodeProfiles(myConfiguration.runnerParameters.profilesMap)
    val runnerSettings = myConfiguration.runnerSettings ?: MavenRunner.getInstance(myConfiguration.project).state
    val generalSettings = myConfiguration.generalSettings ?: MavenProjectsManager.getInstance(myConfiguration.project).generalSettings
    if (encodeProfiles.isNotEmpty()) {
      args.addAll("-P", encodeProfiles)
    }
    runnerSettings.mavenProperties?.forEach {
      args.addProperty(it.key, it.value)
    }
    if (runnerSettings.vmOptions.isNotBlank()) {
      args.add(runnerSettings.vmOptions)
    }

    if (runnerSettings.isSkipTests) {
      args.addProperty("skipTests", "true")
    }

    if (generalSettings.outputLevel == MavenExecutionOptions.LoggingLevel.DEBUG) {
      args.add("--debug")
    }
    if (generalSettings.isNonRecursive) {
      args.add("--non-recursive")
    }
    if (generalSettings.isPrintErrorStackTraces) {
      args.add("--errors")
    }
    if (generalSettings.isAlwaysUpdateSnapshots) {
      args.add("--update-snapshots")
    }
    val threads = generalSettings.threads
    if (!threads.isNullOrBlank()) {
      args.addAll("-T", threads)
    }

    if (generalSettings.userSettingsFile.isNotBlank()) {
      args.addAll("-s", generalSettings.userSettingsFile)
    }
    generalSettings.localRepository.nullize(true)?.also { args.addProperty("-Dmaven.repo.local=$it") }
  }

  private fun addIdeaParameters(args: ParametersList, eel: EelApi) {
    args.addProperty("idea.version", MavenUtil.getIdeaVersionToPassToMavenProcess())
    args.addProperty(
      MavenServerEmbedder.MAVEN_EXT_CLASS_PATH,
      transferContentsIfNonLocal(eel, MavenServerManager.getInstance().getMavenEventListener().toPath()).asEelPath().toString()
    )
  }

  private fun getEnv(existingEnv: Map<String, String>, debug: Boolean): Map<String, String> {
    val map = HashMap<String, String>()
    map.putAll(existingEnv)
    myConfiguration.runnerSettings?.environmentProperties?.let { map.putAll(map) }
    val javaParams = myConfiguration.createJavaParameters(myConfiguration.project)
    map.put("JAVA_HOME", Path(javaParams.jdkPath).asEelPath().toString())
    if (debug && myRemoteConnection != null) {
      val maven_opts = map["MAVEN_OPTS"] ?: ""
      map["MAVEN_OPTS"] = myRemoteConnection!!.enhanceMavenOpts(maven_opts)
    }
    return map
  }

  private fun getScriptPath(eel: EelApi): String {
    val type: MavenHomeType = MavenProjectsManager.getInstance(myConfiguration.project).getGeneralSettings().getMavenHomeType()
    if (type is MavenWrapper) {
      val multimoduleDir = MavenDistributionsCache.getInstance(myConfiguration.project)
        .getMultimoduleDirectory(myConfiguration.runnerParameters.workingDirPath)
      val relativePath = Path(myConfiguration.runnerParameters.workingDirPath).relativize(Path(multimoduleDir)).toString()
      return if (relativePath.isEmpty()) {
        if (isWindows()) "mvnw.cmd" else "./mvnw"
      }
      else {
        if (isWindows()) relativePath.replace("/", "\\") + "\\mvnw.cmd" else "$relativePath/mvnw"
      }
    }

    val distribution = MavenDistributionsCache.getInstance(myConfiguration.project)
      .getMavenDistribution(myConfiguration.runnerParameters.workingDirPath)

    var mavenHome = distribution.mavenHome

    if (type is BundledMaven3 || type is BundledMaven4) {
      mavenHome = transferContentsIfNonLocal(eel, mavenHome)
    }

    if (distribution is DaemonedMavenDistribution) {
     return distribution.daemonHome.resolve("bin").resolve(if (isWindows()) "mvnd.cmd" else "mvnd.sh").asEelPath().toString()
    }
    else {
      return mavenHome.resolve("bin").resolve(if (isWindows()) "mvn.cmd" else "mvn").asEelPath().toString()
    }
  }

  override fun getRemoteConnection(): RemoteConnection? {
    if (myRemoteConnection == null) {
      myRemoteConnection = createRemoteConnection()
    }
    return myRemoteConnection
  }

  private fun createRemoteConnection(): MavenRemoteConnection? {
    for (creator in MavenExtRemoteConnectionCreator.EP_NAME.extensionList) {
      val connection = creator.createRemoteConnectionForScript(myConfiguration)
      if (connection != null) {
        myRemoteConnection = connection
        return connection
      }
    }
    return null
  }

  private fun isWindows() =
    myConfiguration.project.getEelDescriptor().operatingSystem == EelPath.OS.WINDOWS
}
