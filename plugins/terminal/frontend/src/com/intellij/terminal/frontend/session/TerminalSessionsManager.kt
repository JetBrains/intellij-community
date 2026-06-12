package com.intellij.terminal.frontend.session

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.util.CONNECTOR_CLOSING_TIMEOUT
import org.jetbrains.plugins.terminal.util.closeAndWaitFor
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.PROJECT)
class TerminalSessionsManager(private val project: Project) {
  private val sessionsMap = ConcurrentHashMap<TerminalSessionId, TerminalSession>()
  private val connectors = Collections.synchronizedList<TtyConnector>(ArrayList())

  init {
    project.messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosing(project: Project) {
        if (project != this@TerminalSessionsManager.project) {
          return
        }
        runWithModalProgressBlocking(project, TerminalBundle.message("closing.terminal.processes.progress")) {
          closeTerminalProcesses()
        }
      }
    })
  }

  /**
   * Starts the terminal process using provided [options] and wraps it into [TerminalSession].
   * Also, it installs the port forwarding feature.
   *
   * The created session lifecycle is bound to the [scope]. If it cancels, then the process will be terminated.
   * And if the process is terminated on its own, for example, if user executes `exit` or press Ctrl+D,
   * then the [scope] will be canceled as well.
   */
  fun startSession(options: ShellStartupOptions, scope: CoroutineScope): TerminalSessionStartResult {
    val termSize = options.initialTermSize ?: run {
      LOG.warn("No initial terminal size provided, using default 80x24. $options")
      TermSize(80, 24)
    }
    val optionsWithSize = options.builder().initialTermSize(termSize).build()

    val (ttyConnector, configuredOptions) = startTerminalProcess(project, optionsWithSize)
    storeConnector(ttyConnector, scope)
    val observableTtyConnector = ObservableTtyConnector(ttyConnector)

    // Create the JediTerm session scope as the child of the main scope.
    // If the original session terminates on its own, then StateAwareTerminalSession will handle the TerminalSessionTerminatedEvent
    // and cancel the main scope.
    val jediTermScope = scope.childScope("JediTerm session")
    val jediTermSession = createTerminalSession(project, observableTtyConnector, configuredOptions, JBTerminalSystemSettingsProvider(), jediTermScope)

    // It should be guaranteed that the shell command and working directory are not null.
    val options = TerminalStartupOptionsImpl(
      shellCommand = configuredOptions.shellCommand!!,
      workingDirectory = configuredOptions.workingDirectory!!,
      envVariables = configuredOptions.envVariables,
      processType = configuredOptions.processType,
      pid = getLocalPid(ttyConnector),
    )
    val stateAwareSession = StateAwareTerminalSession(jediTermSession, options, scope)

    val sessionId = storeSession(stateAwareSession, scope)

    return TerminalSessionStartResult(
      configuredOptions,
      sessionId,
      observableTtyConnector
    )
  }

  fun getSession(id: TerminalSessionId): TerminalSession? {
    return sessionsMap[id]
  }

  private fun storeSession(session: TerminalSession, scope: CoroutineScope): TerminalSessionId {
    val sessionId = TerminalSessionId(sessionIdCounter.getAndIncrement())
    sessionsMap.put(sessionId, session)
    scope.awaitCancellationAndInvoke {
      sessionsMap.remove(sessionId)
    }
    return sessionId
  }

  private fun storeConnector(ttyConnector: TtyConnector, scope: CoroutineScope) {
    connectors.add(ttyConnector)
    scope.coroutineContext.job.invokeOnCompletion {
      connectors.remove(ttyConnector)
    }
  }

  /**
   * Returns a process ID if this process is running in the same machine as the IDE.
   */
  private fun getLocalPid(ttyConnector: TtyConnector): Long? {
    val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(ttyConnector) ?: run {
      LOG.warn("Failed to get ProcessTtyConnector from $ttyConnector.")
      return null
    }

    return try {
      processTtyConnector.process.pid()
    }
    catch (_: UnsupportedOperationException) {
      // IjentChildPtyProcessAdapter doesn't return a real PID of a remote process
      null
    }
    catch (ex: Exception) {
      LOG.warn("Failed to get pid of the started process: $ttyConnector", ex)
      null
    }
  }

  private suspend fun closeTerminalProcesses() = coroutineScope {
    // Perform closing activities asynchronously for every process
    // because closing sequentially may take much more time
    val tasks = connectors.map {
      async { it.closeAndWaitFor(CONNECTOR_CLOSING_TIMEOUT) }
    }
    tasks.awaitAll()
  }

  companion object {
    private val sessionIdCounter = AtomicInteger(0)

    private val LOG = logger<TerminalSessionsManager>()

    @JvmStatic
    fun getInstance(project: Project): TerminalSessionsManager = project.service()
  }
}

@ApiStatus.Internal
data class TerminalSessionStartResult(
  val configuredOptions: ShellStartupOptions,
  val sessionId: TerminalSessionId,
  val ttyConnector: ObservableTtyConnector,
)