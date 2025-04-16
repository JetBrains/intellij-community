package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.session.TerminalSession
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.APP)
internal class TerminalSessionsManager {
  private val sessionsMap = ConcurrentHashMap<TerminalSessionId, TerminalSession>()

  /**
   * Starts the terminal process using provided [options] and wraps it into [com.intellij.terminal.session.TerminalSession].
   * Also, it installs the port forwarding feature.
   *
   * The created session lifecycle is bound to the [scope]. If it cancels, then the process will be terminated.
   * And if the process is terminated on its own, then the [scope] will be canceled as well.
   */
  suspend fun startSession(
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
    val session = createTerminalSession(project, observableTtyConnector, termSize, JBTerminalSystemSettingsProvider(), scope)
    val stateAwareSession = StateAwareTerminalSession(session)

    val sessionId = storeSession(stateAwareSession, scope)

    val portForwardingScope = scope.childScope("PortForwarding")
    val portForwardingId = TerminalPortForwardingManager.getInstance(project).setupPortForwarding(observableTtyConnector, portForwardingScope)

    return TerminalSessionStartResult(
      configuredOptions,
      sessionId,
      portForwardingId
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

  companion object {
    private val sessionIdCounter = AtomicInteger(0)

    private val LOG = logger<TerminalSessionsManager>()

    @JvmStatic
    fun getInstance(): TerminalSessionsManager = service()
  }
}

internal data class TerminalSessionStartResult(
  val configuredOptions: ShellStartupOptions,
  val sessionId: TerminalSessionId,
  val portForwardingId: TerminalPortForwardingId?,
)