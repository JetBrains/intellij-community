// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParamsGroup
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.getTargetEnvironmentValue
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.Content
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSessionStartedResult
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.actions.requestFocus
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PydevConsoleRunnerFactory
import com.jetbrains.python.console.PydevConsoleRunnerImpl
import com.jetbrains.python.console.PythonConsoleView
import com.jetbrains.python.console.PythonDebugConsoleCommunication
import com.jetbrains.python.console.PythonDebugLanguageConsoleView
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener
import com.jetbrains.python.debugger.settings.PyDebuggerSettings
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.CommandLinePatcher
import com.jetbrains.python.run.DebugAwareConfiguration
import com.jetbrains.python.run.EnvironmentController
import com.jetbrains.python.run.PlainEnvironmentController
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.PythonModuleExecution
import com.jetbrains.python.run.PythonScriptCommandLineState
import com.jetbrains.python.run.PythonScriptExecution
import com.jetbrains.python.run.PythonScriptTargetedCommandLineBuilder
import com.jetbrains.python.run.PythonToolExecution
import com.jetbrains.python.run.PythonToolModuleExecution
import com.jetbrains.python.run.PythonToolScriptExecution
import com.jetbrains.python.run.TargetEnvironmentController
import com.jetbrains.python.run.asyncPromise
import com.jetbrains.python.run.extendEnvs
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.prepareHelperScriptViaToolExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.getOrCreateAdditionalDataOld
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.PYTHONPATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.resolvedPromise
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

open class PyDebugRunner : AsyncProgramRunner<RunnerSettings>() {

  private var pyDebugProcess: PyDebugProcess? = null

  override fun getRunnerId(): String = PY_DEBUG_RUNNER

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    if (DefaultDebugExecutor.EXECUTOR_ID != executorId) {
      // If not debug at all
      return false
    }
    // Any python configuration is debuggable unless it explicitly declares itself as DebugAwareConfiguration and denies it
    // with canRunUnderDebug == false
    if (profile is WrappingRunConfiguration<*>) {
      // If the configuration is wrapper -- unwrap it and check
      return isDebuggable(profile.peer)
    }
    return isDebuggable(profile)
  }

  /**
   * The single launch hook of this runner: [AsyncProgramRunner] opens the run profile and calls this off
   * the run action's own event, so an override must not open one of its own.
   */
  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> =
    execute(environment, state, null)

  private fun execute(
    environment: ExecutionEnvironment,
    state: RunProfileState,
    sessionListener: XDebugSessionListener?,
  ): Promise<RunContentDescriptor?> {
    // aborts the execution of the run configuration if `.canRun` returns false
    // this is used for cases in which a user action prevents the execution; for example,
    // a warning dialog could be displayed to the user asking them if they wish to proceed with
    // running the configuration
    if (state is PythonCommandLineState && !state.canRun()) {
      return resolvedPromise(null)
    }

    return asyncPromise(environment.project) {
      val result = createSessionEx(state, environment).await()
      withContext(Dispatchers.EDT) {
        sessionListener?.let { result.session.addSessionListener(it) }
        result.runContentDescriptor
      }
    }
  }

  /**
   * Creates a debug session and returns the result containing both session and descriptor.
   *
   * @param state the run profile state
   * @param environment the execution environment
   * @return promise with session result containing session and run content descriptor
   */
  @ApiStatus.Internal
  protected open fun createSessionEx(state: RunProfileState, environment: ExecutionEnvironment): Promise<XSessionStartedResult> {
    FileDocumentManager.getInstance().saveAllDocuments()
    return createSessionUsingTargetsApi(state, environment)
  }

  // pydevd only
  private fun createSessionUsingTargetsApi(state: RunProfileState, environment: ExecutionEnvironment): Promise<XSessionStartedResult> {
    val pyState = state as PythonCommandLineState
    val profile = environment.runProfile
    val dataContext = environment.dataContext
    val clientId = ClientId.currentOrNull

    return asyncPromise(environment.project) {
      val pair = try {
        // Re-install the environment data context so that macro expansion works (PY-88858).
        ExecutionManagerImpl.withEnvironmentDataContext(dataContext) {
          val serverSocket = PythonCommandLineState.createServerSocket()
          var debuggerScriptCommandLineBuilder: PythonDebuggerClientModeTargetedCommandLineBuilder? = null
          try {
            val serverLocalPort = serverSocket.localPort
            val localPortBinding = TargetEnvironment.LocalPortBinding(serverLocalPort, null)
            // The local environment does not forward ports, but an SDK without a target can run pydevd on a remote eel
            val eelTunnel = pyState.sdk?.remoteEelOrNull()?.let {
              PyEelDebuggerTunnel.open(it, InetSocketAddress(serverSocket.inetAddress, serverLocalPort))
            }
            val builder = PythonDebuggerClientModeTargetedCommandLineBuilder(
              this@PyDebugRunner, environment.project, pyState, profile, localPortBinding, serverSocket, eelTunnel)
            debuggerScriptCommandLineBuilder = builder
            val result = pyState.execute(environment.executor, builder)!!
            builder.closeEelTunnelOnTermination(result.processHandler)
            Pair(builder.serverSocketForDebugging, result)
          }
          catch (err: Exception) {
            debuggerScriptCommandLineBuilder?.closeEelTunnel()
            closeServerSocket(debuggerScriptCommandLineBuilder?.serverSocketForDebugging ?: serverSocket, err)
            throw err
          }
        }
      }
      catch (err: Exception) {
        throw RuntimeException(err.message, err)
      }

      withContext(Dispatchers.EDT + (clientId?.asContextElement() ?: EmptyCoroutineContext)) {
        createXDebugSession(environment, pyState, pair.first, pair.second)
      }
    }
  }

  private fun createXDebugSession(
    environment: ExecutionEnvironment,
    pyState: PythonCommandLineState,
    serverSocket: ServerSocket,
    result: ExecutionResult,
  ): XSessionStartedResult {
    val starter = object : XDebugProcessStarter() {
      override fun start(session: XDebugSession): XDebugProcess {
        val process = createDebugProcess(session, serverSocket, result, pyState)
        pyDebugProcess = process
        createConsoleCommunication(environment.project, result, process, session)
        return process
      }
    }
    return XDebuggerManager.getInstance(environment.project).newSessionBuilder(starter)
      .environment(environment)
      .startSession()
  }

  protected open fun createDebugProcess(
    session: XDebugSession,
    serverSocket: ServerSocket,
    result: ExecutionResult,
    pyState: PythonCommandLineState,
  ): PyDebugProcess = PyDebugProcess(session, serverSocket, result.executionConsole, result.processHandler, pyState.isMultiprocessDebug)

  internal fun prepareDebuggerScriptExecution(
    project: Project,
    serverPortOnTarget: Function<TargetEnvironment, HostPort>,
    pyState: PythonCommandLineState,
    originalExecution: PythonExecution,
    runProfile: RunProfile?,
    request: HelpersAwareTargetEnvironmentRequest,
  ): PythonExecution {
    val debuggerScript: PythonExecution = if (originalExecution is PythonToolExecution) {
      prepareHelperScriptViaToolExecution(
        PythonHelper.DEBUGGER,
        request,
        originalExecution.toolPath,
        originalExecution.toolParams,
      )
    }
    else {
      prepareHelperScriptExecution(PythonHelper.DEBUGGER, request)
    }

    val targetEnvironmentRequest: TargetEnvironmentRequest = request.targetEnvironmentRequest
    debuggerScript.extendEnvs(originalExecution.envs, targetEnvironmentRequest.targetPlatform)

    debuggerScript.workingDir = originalExecution.workingDir

    originalExecution.accept(object : PythonExecution.Visitor {
      override fun visit(pythonScriptExecution: PythonScriptExecution) {
        // do nothing
      }

      override fun visit(pythonModuleExecution: PythonModuleExecution) {
        // add a module flag only after command line parameters
        debuggerScript.addParameter(MODULE_PARAM)
      }

      override fun visit(pythonToolScriptExecution: PythonToolScriptExecution) {
        // do nothing
      }

      override fun visit(pythonToolModuleExecution: PythonToolModuleExecution) {
        // add a module flag only after command line parameters
        val moduleFlag = pythonToolModuleExecution.moduleFlag
        debuggerScript.addParameter(moduleFlag)
      }
    })

    configureDebugParameters(project, pyState, debuggerScript, false)

    // TODO [Targets API] This workaround is required until Cython extensions are uploaded using Targets API
    val isLocalTarget = targetEnvironmentRequest is LocalTargetEnvironmentRequest
    configureDebugEnvironment(project, TargetEnvironmentController(debuggerScript.envs, request), runProfile, isLocalTarget)

    configureClientModeDebugConnectionParameters(debuggerScript, serverPortOnTarget)

    originalExecution.accept(object : PythonExecution.Visitor {
      override fun visit(pythonScriptExecution: PythonScriptExecution) {
        val scriptPath = pythonScriptExecution.pythonScriptPath
        if (scriptPath != null) {
          debuggerScript.addParameter(scriptPath)
        }
        else {
          throw IllegalArgumentException("Python script path must be set")
        }
      }

      override fun visit(pythonModuleExecution: PythonModuleExecution) {
        val moduleName = pythonModuleExecution.moduleName
        if (moduleName != null) {
          debuggerScript.addParameter(moduleName)
        }
        else {
          throw IllegalArgumentException("Python module name must be set")
        }
      }

      override fun visit(pythonToolScriptExecution: PythonToolScriptExecution) {
        val scriptPath = pythonToolScriptExecution.pythonScriptPath
        debuggerScript.addParameter(scriptPath.andThen { it.toString() })
      }

      override fun visit(pythonToolModuleExecution: PythonToolModuleExecution) {
        val moduleName = pythonToolModuleExecution.moduleName
        debuggerScript.addParameter(moduleName)
      }
    })

    debuggerScript.parameters.addAll(originalExecution.parameters)

    return debuggerScript
  }

  protected open fun configureDebugParameters(
    project: Project,
    pyState: PythonCommandLineState,
    debuggerScript: PythonExecution,
    debuggerScriptInServerMode: Boolean,
  ) {
    if (pyState is PythonScriptCommandLineState && pyState.showCommandLineAfterwards()) {
      debuggerScript.addParameter("--cmd-line")
    }

    if (pyState.isMultiprocessDebug && !debuggerScriptInServerMode) {
      debuggerScript.addParameter(getMultiprocessDebugParameter())
    }

    configureCommonDebugParameters(project, debuggerScript)
  }


  @ApiStatus.Internal
  companion object {
    const val PY_DEBUG_RUNNER: @NonNls String = "PyDebugRunner"

    const val CLIENT_PARAM: @NonNls String = "--client"
    const val PORT_PARAM: @NonNls String = "--port"
    const val FILE_PARAM: @NonNls String = "--file"
    const val MODULE_PARAM: @NonNls String = "--module"
    const val IDE_PROJECT_ROOTS: @NonNls String = "IDE_PROJECT_ROOTS"
    const val LIBRARY_ROOTS: @NonNls String = "LIBRARY_ROOTS"
    const val GEVENT_SUPPORT: @NonNls String = "GEVENT_SUPPORT"
    const val PYDEVD_FILTERS: @NonNls String = "PYDEVD_FILTERS"
    const val PYDEVD_FILTER_LIBRARIES: @NonNls String = "PYDEVD_FILTER_LIBRARIES"
    const val PYDEVD_USE_CYTHON: @NonNls String = "PYDEVD_USE_CYTHON"
    const val PYCHARM_DEBUG: @NonNls String = "PYCHARM_DEBUG"
    const val USE_LOW_IMPACT_MONITORING: @NonNls String = "USE_LOW_IMPACT_MONITORING"
    const val HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME: @NonNls String = "HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME"

    @JvmField
    val CYTHON_EXTENSIONS_DIR: @NonNls String = Path.of(PathManager.getSystemPath(), "cythonExtensions").toString()
    /**
     * A hack for disabling the debugging tracing in the unit-test mode.
     */
    @JvmField
    val FORCE_DISABLE_DEBUGGER_TRACING: Key<Boolean> = Key.create("FORCE_DISABLE_DEBUGGER_TRACING")

    /**
     * Ensures the debug server socket is bound to the address resolved by the target environment's port forwarding.
     * If the resolved host matches the existing socket's bind address (treating all loopback addresses as equivalent),
     * the original socket is reused. Otherwise, a new socket is created on the resolved address, and the original is closed.
     *
     * @return the original `serverSocket` if its address already matches, or a newly created socket bound to the resolved address
     */
    @VisibleForTesting
    @ApiStatus.Internal
    @JvmStatic
    @Throws(IOException::class)
    fun createServerSocketForDebugging(
      environment: TargetEnvironment,
      ideServerPortBinding: TargetEnvironment.LocalPortBinding,
      serverSocket: ServerSocket,
    ): ServerSocket {
      val localPortBinding = environment.localPortBindings[ideServerPortBinding]
      val port = ideServerPortBinding.local
      val hostInetAddress: InetAddress = if (localPortBinding != null) {
        InetAddress.getByName(localPortBinding.localEndpoint.host)
      }
      else {
        LOG.error("The resolution of the local port binding for \"$port\" port cannot be found in the prepared environment" +
                  ", falling back to \"localhost\" for the server socket binding on the local machine")
        InetAddress.getLoopbackAddress()
      }
      LOG.debug("Creating server socket for debugging at $hostInetAddress:$port")
      if (hostInetAddress == serverSocket.inetAddress ||
          (hostInetAddress.isLoopbackAddress && serverSocket.inetAddress.isLoopbackAddress)) {
        return serverSocket
      }
      // Close the pre-created loopback socket BEFORE binding the new one.
      try {
        serverSocket.close()
      }
      catch (e: IOException) {
        LOG.warn("Failed to close the original server socket", e)
      }
      return ServerSocket(port, 0, hostInetAddress)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun <T> createConsoleCommunication(
      project: Project,
      result: ExecutionResult,
      debugProcess: T,
      session: XDebugSession,
    ): PythonDebugConsoleCommunication<T>? where T : XDebugProcess, T : PyDebugProcessWithConsole {
      val console = result.executionConsole
      if (console is PythonDebugLanguageConsoleView) {
        val processHandler = result.processHandler
        return initDebugConsole(project, debugProcess, console, processHandler, session)
      }
      return null
    }

    @ApiStatus.Internal
    @JvmStatic
    fun <T> initDebugConsole(
      project: Project,
      debugProcess: T,
      console: PythonDebugLanguageConsoleView,
      processHandler: ProcessHandler,
      session: XDebugSession,
    ): PythonDebugConsoleCommunication<T> where T : XDebugProcess, T : PyDebugProcessWithConsole {
      val pythonConsoleView: PythonConsoleView = console.pydevConsoleView
      val debugConsoleCommunication = PythonDebugConsoleCommunication(project, debugProcess, pythonConsoleView)

      pythonConsoleView.setConsoleCommunication(debugConsoleCommunication)

      val consoleExecuteActionHandler = PydevDebugConsoleExecuteActionHandler(console, processHandler, debugConsoleCommunication)

      val pythonDebugConsoleCommunication = initDebugConsole(
        pythonConsoleView, consoleExecuteActionHandler, debugProcess, processHandler, debugConsoleCommunication, session)

      // Readiness is unconditional: the Debug Console has to work whichever console is on screen. It can only be
      // marked ready here, because it needs the execute action handler that was attached just above.
      // "Always show Debug Console" then picks the visible one, and nothing else.
      console.initDebugConsole()
      if (console.isEnabled && PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault) {
        console.enableConsole(false)
      }

      return pythonDebugConsoleCommunication
    }

    @ApiStatus.Internal
    @JvmStatic
    protected fun <T> initDebugConsole(
      pythonConsoleView: PythonConsoleView,
      consoleExecuteActionHandler: PydevDebugConsoleExecuteActionHandler,
      debugProcess: T,
      processHandler: ProcessHandler,
      debugConsoleCommunication: PythonDebugConsoleCommunication<T>,
      session: XDebugSession,
    ): PythonDebugConsoleCommunication<T> where T : XDebugProcess, T : PyDebugProcessWithConsole {
      pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler)

      debugProcess.session.addSessionListener(consoleExecuteActionHandler)
      LanguageConsoleBuilder(pythonConsoleView).processHandler(processHandler).initActions(consoleExecuteActionHandler, "py", true)

      debugConsoleCommunication.addCommunicationListener(object : ConsoleCommunicationListener {
        override fun commandExecuted(more: Boolean) {
          session.rebuildViews()
        }

        override fun inputRequested() {
          ApplicationManager.getApplication().invokeLater {
            val debugConsoleView = session.consoleView
            if (debugConsoleView is PythonDebugLanguageConsoleView) {
              val sessionUi = session.ui
              if (sessionUi != null) {
                // In debug mode, selectConsoleTab only uses "Console" tab name, descriptor not needed
                val consoleContent: Content? = sessionUi.contentManager.findContent("Console")
                if (consoleContent != null) {
                  sessionUi.contentManager.setSelectedContent(consoleContent)
                }
              }
              else {
                // TODO [Debugger.RunnerLayoutUi]
              }

              if (pythonConsoleView.isVisible) {
                requestFocus(true, null, pythonConsoleView, true)
              }
              else {
                val primaryConsoleView = debugConsoleView.primaryConsoleView
                if (primaryConsoleView is ConsoleViewImpl) {
                  requestFocus(false, primaryConsoleView.editor, null, true)
                }
              }
            }
          }
        }
      })

      return debugConsoleCommunication
    }

    @JvmStatic
    fun configureDebugEnvironment(project: Project, environment: MutableMap<String, String>, runProfile: RunProfile?) {
      configureDebugEnvironment(project, PlainEnvironmentController(environment), runProfile, true)
    }

    private fun configureDebugEnvironment(
      project: Project,
      environmentController: EnvironmentController,
      runProfile: RunProfile?,
      addCythonExtensionsToPythonPath: Boolean,
    ) {
      if (PyDebuggerOptionsProvider.getInstance(project).isSupportGeventDebugging) {
        environmentController.putFixedValue(GEVENT_SUPPORT, "True")
      }

      val debuggerSettings = PyDebuggerSettings.getInstance()
      if (debuggerSettings.isSteppingFiltersEnabled) {
        environmentController.putFixedValue(PYDEVD_FILTERS, debuggerSettings.getSteppingFiltersForProject(project))
      }
      if (debuggerSettings.isLibrariesFilterEnabled) {
        environmentController.putFixedValue(PYDEVD_FILTER_LIBRARIES, "True")
      }
      if (debuggerSettings.valuesPolicy != ValuesPolicy.SYNC) {
        environmentController.putFixedValue(PyDebugValue.POLICY_ENV_VARS[debuggerSettings.valuesPolicy]!!, "True")
      }

      PydevConsoleRunnerFactory.putDebugConsoleIPythonEnvFlag(project, environmentController)

      if (addCythonExtensionsToPythonPath) {
        environmentController.appendTargetPathToPathsValue(PYTHONPATH, CYTHON_EXTENSIONS_DIR)
      }

      PyDebugAsyncioCustomizer.instance.enableAsyncioMode(environmentController)

      val runConfiguration = runProfile as? AbstractPythonRunConfiguration<*>
      val module = runConfiguration?.module

      if (module != null) {
        addProjectRootsToEnv(module, environmentController)
      }

      if (runConfiguration != null) {
        val sdk = runConfiguration.sdk
        if (sdk != null) {
          val langLevel = languageLevelOf(sdk)
          // PY-28457 Disable Cython extensions in Python 3.4 and Python 3.5 because of a crash in generated C code
          if (langLevel == LanguageLevel.PYTHON34 || langLevel == LanguageLevel.PYTHON35) {
            environmentController.putFixedValue(PYDEVD_USE_CYTHON, "NO")
          }
        }

        addSdkRootsToEnv(environmentController, runConfiguration)
        environmentController.appendTargetPathToPathsValue(PYTHONPATH, runConfiguration.workingDirectorySafe)
      }

      applyRegistryFlags(environmentController)
    }

    @JvmStatic
    fun configureCommonDebugParameters(project: Project, debuggerScript: PythonExecution) {
      if (ApplicationManager.getApplication().isUnitTestMode && !isForceDisableDebuggerTracing()) {
        debuggerScript.addParameter("--DEBUG")
      }

      if (PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures) {
        debuggerScript.addParameter("--save-signatures")
      }

      if (PyDebuggerOptionsProvider.getInstance(project).isSupportQtDebugging) {
        val pyQtBackend = StringUtil.toLowerCase(PyDebuggerOptionsProvider.getInstance(project).pyQtBackend)
        debuggerScript.addParameter("--qt-support=$pyQtBackend")
      }
    }

    private fun isForceDisableDebuggerTracing(): Boolean =
      true == ApplicationManager.getApplication().getUserData(FORCE_DISABLE_DEBUGGER_TRACING)

    /**
     * The part of the legacy implementation based on [GeneralCommandLine].
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated("The part of the legacy implementation based on GeneralCommandLine.")
    @JvmStatic
    fun disableBuiltinBreakpoint(sdk: Sdk?, env: MutableMap<String, String>) {
      if (sdk != null && languageLevelOf(sdk) == LanguageLevel.PYTHON37) {
        env["PYTHONBREAKPOINT"] = "0"
      }
    }

    /**
     * Configure the debugger script in *client mode* to connect to IDE on
     * the execution.
     *
     * @param debuggerScript     the debugger script
     * @param serverPortOnTarget the server
     */
    private fun configureClientModeDebugConnectionParameters(
      debuggerScript: PythonExecution,
      serverPortOnTarget: Function<TargetEnvironment, HostPort>,
    ) {
      // --client
      debuggerScript.addParameter(CLIENT_PARAM)
      debuggerScript.addParameter(serverPortOnTarget.andThen(HostPort::host))
      // --port
      debuggerScript.addParameter(PORT_PARAM)
      debuggerScript.addParameter(serverPortOnTarget.andThen(HostPort::port).andThen { it.toString() })
      // --file
      debuggerScript.addParameter(FILE_PARAM)
    }

    private fun addProjectRootsToEnv(module: Module, environment: EnvironmentController) {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      val contentRoots = moduleRootManager.contentRoots.asSequence() + getDependenciesContentRoots(module)
      environment.putTargetPathsValue(IDE_PROJECT_ROOTS, contentRoots.map { it.path }.toList())
    }

    private fun getDependenciesContentRoots(module: Module): Sequence<VirtualFile> {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      return moduleRootManager.dependencies.asSequence().flatMap { ModuleRootManager.getInstance(it).contentRoots.asSequence() }
    }

    private fun addSdkRootsToEnv(environmentController: EnvironmentController, runConfiguration: AbstractPythonRunConfiguration<*>) {
      val sdk = runConfiguration.sdk
      if (sdk != null) {
        val roots = sdk.rootProvider.getFiles(OrderRootType.CLASSES).map { it.path }
        // Assume that libraries are located on the target machine
        environmentController.putFixedValue(LIBRARY_ROOTS, StringUtil.join(roots, pathSeparator))
      }
    }

    private fun applyRegistryFlags(environmentController: EnvironmentController) {
      if (Registry.`is`("python.debug.low.impact.monitoring.api")) {
        environmentController.putFixedValue(USE_LOW_IMPACT_MONITORING, "True")
      }

      if (!Registry.`is`("python.debug.enable.cython.speedups")) {
        environmentController.putFixedValue(PYDEVD_USE_CYTHON, "NO")
      }

      if (Registry.`is`("python.debug.enable.diagnostic.prints")) {
        environmentController.putFixedValue(PYCHARM_DEBUG, "True")
      }

      if (Registry.`is`("python.debug.halt.variable.resolve.threads.on.step.resume")) {
        environmentController.putFixedValue(HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME, "True")
      }
    }

    /**
     * The flag that enables multiprocess debugging in pydevd. `--multiproc` selects the older dispatcher
     * protocol, which [PyDebugProcess.createMultiprocessDebugger] still implements on the IDE side behind
     * the same registry key; the two must agree on which protocol pydevd speaks.
     */
    private fun getMultiprocessDebugParameter(): String =
      if (Registry.get("python.debugger.use.dispatcher").asBoolean()) "--multiproc" else "--multiprocess"

    private fun closeServerSocket(serverSocket: ServerSocket, cause: Throwable) {
      try {
        serverSocket.close()
      }
      catch (e: IOException) {
        cause.addSuppressed(e)
      }
    }

    private fun isDebuggable(profile: RunProfile): Boolean {
      if (profile is DebugAwareConfiguration) {
        // if configuration knows whether debug is allowed
        return profile.canRunUnderDebug()
      }
      // Any python configuration is debuggable
      return profile is AbstractPythonRunConfiguration<*>
    }

  }

  @TestOnly
  @Throws(ExecutionException::class)
  fun executeWithListener(environment: ExecutionEnvironment, sessionListener: XDebugSessionListener?) {
    val state = environment.state ?: return
    ExecutionManager.getInstance(environment.project).startRunProfile(environment) { execute(environment, state, sessionListener) }
  }

  @TestOnly
  fun getProcess(): PyDebugProcess? = pyDebugProcess

  @TestOnly
  fun resetProcess() {
    pyDebugProcess = null
  }

  /**
   * The part of the legacy implementation based on [GeneralCommandLine].
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("The part of the legacy implementation based on GeneralCommandLine.")
  fun createCommandLinePatchers(
    project: Project,
    state: PythonCommandLineState,
    profile: RunProfile,
    serverLocalPort: Int,
  ): Array<CommandLinePatcher> {
    val debugServerPatcher = CommandLinePatcher { commandLine ->
      // script name is the last parameter; all other params are for python interpreter; insert just before the name
      val parametersList = commandLine.parametersList

      val debugParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER)
      assert(debugParams != null)

      // was patchExeParams(parametersList): strip '-m' from the module params group, remembering whether it was a module run
      val moduleParamsIndex = parametersList.paramsGroups.indexOf(parametersList.getParamsGroup(PythonCommandLineState.GROUP_MODULE))
      val oldModuleParams = parametersList.removeParamsGroup(moduleParamsIndex)
      var isModule = false
      if (oldModuleParams != null) {
        val newModuleParams = ParamsGroup(PythonCommandLineState.GROUP_MODULE)
        for (param in oldModuleParams.parameters) {
          if (param != "-m") {
            newModuleParams.addParameter(param)
          }
          else {
            isModule = true
          }
        }
        parametersList.addParamsGroupAt(moduleParamsIndex, newModuleParams)
      }

      PythonHelper.DEBUGGER.addToGroup(debugParams!!, commandLine)

      if (isModule) {
        // add a module flag only after command line parameters
        debugParams.addParameter(MODULE_PARAM)
      }

      if (state.isMultiprocessDebug) {
        debugParams.addParameter(getMultiprocessDebugParameter())
      }

      // was configureCommonDebugParameters(project, debugParams)
      if (ApplicationManager.getApplication().isUnitTestMode && !isForceDisableDebuggerTracing()) {
        debugParams.addParameter("--DEBUG")
      }
      if (PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures) {
        debugParams.addParameter("--save-signatures")
      }
      if (PyDebuggerOptionsProvider.getInstance(project).isSupportQtDebugging) {
        val pyQtBackend = StringUtil.toLowerCase(PyDebuggerOptionsProvider.getInstance(project).pyQtBackend)
        debugParams.addParameter("--qt-support=$pyQtBackend")
      }

      configureDebugEnvironment(project, commandLine.environment, profile)

      // was configureDebugConnectionParameters(debugParams, serverLocalPort)
      for (arg in arrayOf(CLIENT_PARAM, "127.0.0.1", PORT_PARAM, serverLocalPort.toString(), FILE_PARAM)) {
        debugParams.addParameter(arg)
      }

      val exeParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS)

      val flavor = state.sdkFlavor
      if (flavor != null) {
        assert(exeParams != null)
        for (option in flavor.extraDebugOptions) {
          exeParams!!.addParameter(option)
        }
      }
    }

    // was createRunConfigPatcher(state, profile): state is always a PythonCommandLineState here, so only the profile matters
    val runConfigPatcher = profile as? AbstractPythonRunConfiguration<*>

    return listOfNotNull(debugServerPatcher, runConfigPatcher).toTypedArray()
  }
}

/**
 * Builder class for creating a command line configured for debugging Python scripts in the client mode, when
 * the debugger process connects to an IDE.
 */
private class PythonDebuggerClientModeTargetedCommandLineBuilder(
  private val runner: PyDebugRunner,
  private val project: Project,
  private val pyState: PythonCommandLineState,
  private val profile: RunProfile,
  private val localPortBinding: TargetEnvironment.LocalPortBinding,
  /**
   * The server socket reserved before creation of the environment and adjusted after it's prepared.
   */
  @Volatile var serverSocketForDebugging: ServerSocket,
  /**
   * The tunnel from the remote eel of an SDK without a target to the IDE server socket, or `null` for any other SDK.
   */
  private val eelTunnel: PyEelDebuggerTunnel?,
) : PythonScriptTargetedCommandLineBuilder {

  override fun build(
    helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
    pythonScript: PythonExecution,
  ): PythonExecution {
    val portBinding = createPortBinding(helpersAwareTargetRequest)
    val debuggerScript = runner.prepareDebuggerScriptExecution(project, portBinding, pyState, pythonScript, profile, helpersAwareTargetRequest)

    val configuredInterpreterParameters = pyState.configuredInterpreterParameters

    val flavor = pyState.sdkFlavor
    if (flavor != null) {
      debuggerScript.additionalInterpreterParameters.addAll(flavor.extraDebugOptions)
    }

    debuggerScript.additionalInterpreterParameters.addAll(
      createInterpreterParametersToPreventPycGenerationInHelpersDir(configuredInterpreterParameters))

    debuggerScript.charset = PydevConsoleRunnerImpl.CONSOLE_CHARSET

    return debuggerScript
  }

  private fun createPortBinding(
    helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
  ): Function<TargetEnvironment, HostPort> {
    helpersAwareTargetRequest.targetEnvironmentRequest.localPortBindings.add(localPortBinding)
    helpersAwareTargetRequest.targetEnvironmentRequest.onEnvironmentPrepared { environment, _ ->
      try {
        serverSocketForDebugging = PyDebugRunner.createServerSocketForDebugging(environment, localPortBinding, serverSocketForDebugging)
      }
      catch (e: IOException) {
        throw RuntimeException("Unable to create server socket for debugging", e)
      }
    }
    helpersAwareTargetRequest.targetEnvironmentRequest.localPortBindings.add(localPortBinding)
    val tunnel = eelTunnel
    if (tunnel != null && helpersAwareTargetRequest.targetEnvironmentRequest is LocalTargetEnvironmentRequest) {
      return Function { tunnel.hostPort }
    }
    return localPortBinding.getTargetEnvironmentValue()
  }

  /**
   * Closes the tunnel to the eel of the SDK, if there is one, when the debugged process terminates.
   */
  fun closeEelTunnelOnTermination(processHandler: ProcessHandler) {
    val tunnel = eelTunnel ?: return
    processHandler.addProcessListener(object : ProcessListener {
      override fun processTerminated(event: ProcessEvent) {
        tunnel.close()
      }
    })
    if (processHandler.isProcessTerminated) {
      tunnel.close()
    }
  }

  fun closeEelTunnel() {
    eelTunnel?.close()
  }

  private fun createInterpreterParametersToPreventPycGenerationInHelpersDir(
    existingInterpreterParameters: List<String>,
  ): List<String> {
    val sdk = pyState.sdk ?: return emptyList()
    // The cache directory is on the IDE machine, so an interpreter on a remote eel cannot use it
    if (PythonSdkUtil.isRemote(sdk) || sdk.remoteEelOrNull() != null) return emptyList()

    sdk.versionString ?: return emptyList()

    val pythonSdkFlavor = getOrCreateAdditionalDataOld(sdk).flavor
    if (pythonSdkFlavor !is CPythonSdkFlavor) return emptyList()

    // Note that we don't modify the parameters if the user already sets the options.
    if (existingInterpreterParameters.contains(PYTHON_DONT_WRITE_PYC_FLAG)) return emptyList()

    if (languageLevelOf(sdk).isOlderThan(LanguageLevel.PYTHON38)) {
      // There is no option for defining a custom directory for .pyc files in Python 3.7 and older, thus disable cache generation entirely.
      return listOf(PYTHON_DONT_WRITE_PYC_FLAG)
    }
    else {
      for (i in existingInterpreterParameters.indices) {
        if (existingInterpreterParameters[i].startsWith(PYTHON3_PYCACHE_PREFIX_OPTION) &&
            i > 0 && existingInterpreterParameters[i - 1] == "-X") {
          return emptyList()
        }
      }
      return try {
        listOf("-X", PYTHON3_PYCACHE_PREFIX_OPTION + prepareAndGetPycacheDirectory())
      }
      catch (_: IOException) {
        listOf(PYTHON_DONT_WRITE_PYC_FLAG)
      }
    }
  }
}

private const val PYTHON_DONT_WRITE_PYC_FLAG = "-B"
private const val PYTHON3_PYCACHE_PREFIX_OPTION = "pycache_prefix="
private val pathSeparator: String get() = File.pathSeparator

private val LOG = Logger.getInstance(PyDebugRunner::class.java)

private fun languageLevelOf(sdk: Sdk): LanguageLevel {
  val version = sdk.versionString
  val level = version?.let { LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(it) }
  return level ?: LanguageLevel.getDefault()
}

private fun prepareAndGetPycacheDirectory(): Path {
  val pycacheDir = PathManager.getSystemDir().resolve("cpython-cache")
  Files.createDirectories(pycacheDir)
  return pycacheDir.toAbsolutePath()
}
