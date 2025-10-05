package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.APP)
class TerminalSessionsManager {
  private val sessionsMap = ConcurrentHashMap<TerminalSessionId, BackendTerminalSession>()

  /**
   * Starts the terminal process using provided [options] and wraps it into [com.intellij.terminal.backend.BackendTerminalSession].
   * Also, it installs the port forwarding feature.
   *
   * The created session lifecycle is bound to the [scope]. If it cancels, then the process will be terminated.
   * And if the process is terminated on its own, for example, if user executes `exit` or press Ctrl+D,
   * then the [scope] will be canceled as well.
   */
  fun startSession(
    options: ShellStartupOptions,
    project: Project,
    scope: CoroutineScope,
  ): TerminalSessionStartResult {
    val termSize = options.initialTermSize ?: run {
      LOG.warn("No initial terminal size provided, using default 80x24. $options")
      TermSize(80, 24)
    }
    val optionsWithSize = options.builder().initialTermSize(termSize).build()

    val (ttyConnector, configuredOptions) = startTerminalProcess(project, optionsWithSize)
    val observableTtyConnector = ObservableTtyConnector(ttyConnector)

    // Create the JediTerm session scope as the child of the main scope.
    // If the original session terminates on its own, then StateAwareTerminalSession will handle the TerminalSessionTerminatedEvent
    // and cancel the main scope.
    val jediTermScope = scope.childScope("JediTerm session")
    val jediTermSession = createTerminalSession(project, observableTtyConnector, configuredOptions, JBTerminalSystemSettingsProvider(), jediTermScope)
    val stateAwareSession = StateAwareTerminalSession(project, jediTermSession, scope)

    val sessionId = storeSession(stateAwareSession, scope)

    return TerminalSessionStartResult(
      configuredOptions,
      sessionId,
      observableTtyConnector
    )
  }

  fun getSession(id: TerminalSessionId): BackendTerminalSession? {
    return sessionsMap[id]
  }

  private fun storeSession(session: BackendTerminalSession, scope: CoroutineScope): TerminalSessionId {
    val sessionId = TerminalSessionId(sessionIdCounter.getAndIncrement())
    sessionsMap.put(sessionId, session)
    scope.awaitCancellationAndInvoke {
      sessionsMap.remove(sessionId)
    }
    return sessionId
  }

  companion object {
    private val sessionIdCounter = AtomicInteger(0)

    private val LOG = logger<TerminalSessionsManager>()

    @JvmStatic
    fun getInstance(): TerminalSessionsManager = service()
  }
}

@ApiStatus.Internal
data class TerminalSessionStartResult(
  val configuredOptions: ShellStartupOptions,
  val sessionId: TerminalSessionId,
  val ttyConnector: ObservableTtyConnector,
)