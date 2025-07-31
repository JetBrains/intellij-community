// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildView
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.*
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteConnectionCreator
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.search.ExecutionSearchScopes
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor
import org.jetbrains.idea.maven.execution.*
import org.jetbrains.idea.maven.execution.MavenExternalParameters.encodeProfiles
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.Path
import kotlin.io.path.exists

class MavenShCommandLineState(val environment: ExecutionEnvironment, private val myConfiguration: MavenRunConfiguration) : RunProfileState, RemoteConnectionCreator {
  private var mavenConnectionWrapper: MavenRemoteConnectionWrapper? = null
  private val workingDir: EelPath by lazy {
    Path(myConfiguration.runnerParameters.workingDirPath).asEelPath()
  }

  @Throws(ExecutionException::class)

  private fun startProcess(debug: Boolean): ProcessHandler {
    return runWithModalProgressBlocking(myConfiguration.project, RunnerBundle.message("maven.target.run.label")) {
      val eelApi = myConfiguration.project.getEelDescriptor().toEelApi()

      val exe = if (isWindows()) "cmd.exe" else "/bin/sh"
      val env = getEnv(eelApi.exec.fetchLoginShellEnvVariables(), debug)
      val args = getArgs(eelApi)

      val processHandler = runProcessInEel(eelApi, exe, env, args)
      JavaRunConfigurationExtensionManager.instance
        .attachExtensionsToProcess(myConfiguration, processHandler, environment.runnerSettings)
      return@runWithModalProgressBlocking processHandler
    }
  }

  private suspend fun runProcessInEel(
    eelApi: EelApi,
    exe: String,
    env: Map<String, String>,
    params: ParametersList,
  ): KillableColoredProcessHandler.Silent {


    return if (isWindows()) {
      prepareBatScriptForWindows(params, exe, eelApi, env)
    }
    else {
      doRunProcessInEel(eelApi, exe, env, params.list, "$exe ${params.list.joinToString(" ")}", null)
    }
  }

  private suspend fun prepareBatScriptForWindows(
    params: ParametersList,
    exe: String,
    eelApi: EelApi,
    env: Map<String, String>,
  ): KillableColoredProcessHandler.Silent {
    val cmdArgs = listOf("/c",
                         params.parameters
                           .joinToString(separator = " ", prefix = "\"", postfix = "\"") {
                             CommandLineUtil.escapeParameterOnWindows(it, true)
                           }
    )
    val pair = writeParamsToBatBecauseCmdIsReallyReallyBad(exe, cmdArgs)
    var pathForCmd: String = pair.first
    val tmpBat: File = pair.second

    val commandLineForUser = if (Registry.`is`("maven.spy.events.debug")) {
      "cmd.exe /c $pathForCmd"
    }
    else {
      "$exe ${cmdArgs.joinToString(" ")}"
    }

    return doRunProcessInEel(eelApi, exe, env, listOf("/c", pathForCmd), commandLineForUser) {
      try {
        tmpBat.delete()
      }
      catch (ignore: IOException) {
      }
    }
  }

  private fun writeParamsToBatBecauseCmdIsReallyReallyBad(exe: String, cmdArgs: List<String>): Pair<String, File> {
    val tempDirectory = FileUtilRt.getTempDirectory()
    var pathForCmd: String
    val tmpBat: File
    if (!tempDirectory.contains(" ")) {
      @SuppressWarnings("IO_FILE_USAGE") // Here should be java.io.File
      tmpBat = FileUtil.createTempFile(File(tempDirectory), "mvn-idea-exec", ".bat", false, true)
      pathForCmd = tmpBat.absolutePath
    }
    else {
      @SuppressWarnings("IO_FILE_USAGE") // Here should be java.io.File
      tmpBat = FileUtil.createTempFile(File(myConfiguration.runnerParameters.workingDirPath), "mvn-idea-exec", ".bat", false, true)
      pathForCmd = tmpBat.name
    }
    tmpBat.writeText("@$exe ${cmdArgs.joinToString(" ")}")
    return Pair(pathForCmd, tmpBat)
  }


  private suspend fun doRunProcessInEel(
    eelApi: EelApi,
    exe: String,
    env: Map<String, String>,
    args: List<String>,
    commandLineToShowUser: String,
    onTerminate: (() -> Unit)?,
  ): KillableColoredProcessHandler.Silent {
    val processOptions = eelApi.exec.spawnProcess(exe)
      .env(env)
      .workingDirectory(workingDir)
      .args(args)

    try {
      val result = processOptions.eelIt()
      return KillableColoredProcessHandler.Silent(result.convertToJavaProcess(), commandLineToShowUser, Charsets.UTF_8, emptySet())
        .also {
          it.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
              onTerminate?.invoke()
            }
          })
        }
    } catch (e: ExecuteProcessException) {
      MavenLog.LOG.warn("Cannot execute maven goal: errcode: ${e.errno}, message:  ${e.message}")
      throw ExecutionException(e.message)
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
      MavenBuildEventProcessor(myConfiguration, buildView!!, descriptor, taskId, { it }, Function { ctx: MavenParsingContext? -> StartBuildEventImpl(descriptor, "") }, isWrapperedOutput())

    processHandler.addProcessListener(BuildToolConsoleProcessAdapter(eventProcessor))
    buildView.attachToProcess(MavenHandlerFilterSpyWrapper(processHandler, isWrapperedOutput(), isWindows()))

    return DefaultExecutionResult(buildView, processHandler)
  }

  @Throws(ExecutionException::class)
  private fun createBuildView(
    descriptor: BuildDescriptor,
  ): BuildView? {
    val console = createConsole()
    val project = myConfiguration.project
    val viewManager = project.getService<ExternalSystemRunConfigurationViewManager>(ExternalSystemRunConfigurationViewManager::class.java)
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
    val viewManager = environment.project.getService<BuildViewManager>(BuildViewManager::class.java)

    descriptor.withProcessHandler(MavenBuildHandlerFilterSpyWrapper(processHandler, isWrapperedOutput(), isWindows()), null)
    descriptor.withExecutionEnvironment(environment)
    val startBuildEvent = StartBuildEventImpl(descriptor, "")
    val eventProcessor =
      MavenBuildEventProcessor(myConfiguration, viewManager, descriptor, taskId,
                               { it }, { startBuildEvent }, isWrapperedOutput())

    processHandler.addProcessListener(BuildToolConsoleProcessAdapter(eventProcessor))
    val res = DefaultExecutionResult(consoleView, processHandler, DefaultActionGroup())
    res.setRestartActions(JvmToggleAutoTestAction())
    return res
  }


  private fun getArgs(eel: EelApi): ParametersList {
    val args = ParametersList()
    args.add(getScriptPath(eel))
    addIdeaParameters(args, eel)
    addSettingParameters(args)
    args.addAll(myConfiguration.runnerParameters.options)
    args.addAll(myConfiguration.runnerParameters.goals)
    return args
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
      args.addAll("-s", generalSettings.userSettingsFile.asTargetPathString())
    }
    if (generalSettings.localRepository.isNotBlank()) {
      args.addProperty("-Dmaven.repo.local=${MavenSettingsCache.getInstance(myConfiguration.project).getEffectiveUserLocalRepo()}")
    }
  }

  private fun addIdeaParameters(args: ParametersList, eel: EelApi) {
    args.addProperty("idea.version", MavenUtil.getIdeaVersionToPassToMavenProcess())
    args.addProperty(
      MavenServerEmbedder.MAVEN_EXT_CLASS_PATH,
      transferLocalContentToRemote(
        source = MavenServerManager.getInstance().getMavenEventListener().toPath(),
        target = TransferTarget.Temporary(eel.descriptor)
      ).asEelPath().toString()
    )
    args.addProperty("jansi.passthrough", "true")
    args.addProperty("style.color", "always")
  }

  private fun getEnv(existingEnv: Map<String, String>, debug: Boolean): Map<String, String> {
    val map = HashMap<String, String>()
    map.putAll(existingEnv)
    myConfiguration.runnerSettings?.environmentProperties?.let { map.putAll(map) }
    val javaParams = myConfiguration.createJavaParameters(myConfiguration.project)
    map["JAVA_HOME"] = Path(javaParams.jdkPath).asEelPath().toString()
    if (debug && mavenConnectionWrapper != null) {
      val maven_opts = map["MAVEN_OPTS"] ?: ""
      map["MAVEN_OPTS"] = mavenConnectionWrapper!!.enhanceMavenOpts(maven_opts)
    }
    myConfiguration.runnerSettings?.environmentProperties?.let { map.putAll(it) }
    return map
  }


  private fun getScriptPath(eel: EelApi): String {
    val type: MavenHomeType = MavenProjectsManager.getInstance(myConfiguration.project).getGeneralSettings().getMavenHomeType()
    if (type is MavenWrapper) {
      val path = getPathToWrapperScript()
      val file = workingDir.resolve(path)
      if (file.asNioPath().exists()) {
        return path
      }
      return getMavenExecutablePath(BundledMaven3, eel)
    }

    return getMavenExecutablePath(type, eel)
  }

  private fun getMavenExecutablePath(type: MavenHomeType, eel: EelApi): String {
    val distribution = MavenDistributionsCache.getInstance(myConfiguration.project)
      .getMavenDistribution(myConfiguration.runnerParameters.workingDirPath)

    var mavenHome = distribution.mavenHome

    if (type is BundledMaven) {
      mavenHome = transferLocalContentToRemote(mavenHome, TransferTarget.Temporary(eel.descriptor))
    }

    if (distribution is DaemonedMavenDistribution) {
      return distribution.daemonHome.resolve("bin").resolve(if (isWindows()) "mvnd.cmd" else "mvnd.sh").asEelPath().toString()
    }
    else {
      return mavenHome.resolve("bin").resolve(if (isWindows()) "mvn.cmd" else "mvn").asEelPath().toString()
    }
  }

  private fun getPathToWrapperScript(): String {
    val multimoduleDir = MavenDistributionsCache.getInstance(myConfiguration.project)
      .getMultimoduleDirectory(myConfiguration.runnerParameters.workingDirPath)
      .let { Path.of(it) }
    val workingDirPath = Path(myConfiguration.runnerParameters.workingDirPath)
    val relativePath = workingDirPath.relativizeWithTargetOsSeparators(multimoduleDir)
    return when {
      relativePath.isEmpty() || relativePath == "." ->
        when {
          isWindows() -> "mvnw.cmd"
          else -> "./mvnw"
        }
      else -> {
        when {
          isWindows() -> "$relativePath\\mvnw.cmd"
          else -> "$relativePath/mvnw"
        }
      }
    }
  }

  private fun Path.relativizeWithTargetOsSeparators(other: Path): String {
    val relativePath = relativize(other).toString()
    val hostSeparator = if (LocalEelDescriptor.osFamily.isWindows) "\\" else "/"
    val targetSeparator = if (getEelDescriptor().osFamily.isWindows)  "\\" else "/"
    return if (hostSeparator == targetSeparator) {
      relativePath
    } else {
      relativePath.replace(hostSeparator, targetSeparator)
    }
  }

  private fun getOrCreateRemoteConnection(): MavenRemoteConnectionWrapper? {
    if (mavenConnectionWrapper == null) {
      mavenConnectionWrapper = createRemoteConnection()
    }
    return mavenConnectionWrapper
  }

  private fun createRemoteConnection(): MavenRemoteConnectionWrapper? {
    for (creator in MavenExtRemoteConnectionCreator.EP_NAME.extensionList) {
      val connection = creator.createRemoteConnectionForScript(myConfiguration)
      if (connection != null) {
        mavenConnectionWrapper = connection
        return connection
      }
    }
    return null
  }

  private fun isWindows() =
    when (myConfiguration.project.getEelDescriptor().osFamily) {
      EelOsFamily.Posix -> false
      EelOsFamily.Windows -> true
    }

  private fun isWrapperedOutput(): Boolean {
    val mavenDistribution = MavenDistributionsCache.getInstance(myConfiguration.project).getMavenDistribution(myConfiguration.runnerParameters.workingDirPath)
    return mavenDistribution.isMaven4() || mavenDistribution is DaemonedMavenDistribution
  }

  private fun String.asTargetPathString(): String = Path.of(this).asEelPath().toString()

  override fun createRemoteConnection(environment: ExecutionEnvironment?): RemoteConnection? {
    return createRemoteConnection()?.myRemoteConnection
  }

  override fun isPollConnection(): Boolean = true

}
