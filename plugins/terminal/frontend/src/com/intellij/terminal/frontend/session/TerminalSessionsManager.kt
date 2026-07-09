package com.intellij.terminal.frontend.session

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
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
    val started = startStandardTerminalSession(project = project, options = options, scope = scope)
    storeConnector(started.ttyConnector, scope)
    val sessionId = storeSession(started.session, scope)

    return TerminalSessionStartResult(
      started.session,
      started.configuredOptions,
      sessionId,
      started.ttyConnector,
    )
  }

  fun getSession(id: TerminalSessionId): TerminalSession? {
    return sessionsMap[id]
  }

  private fun storeSession(session: TerminalSession, scope: CoroutineScope): TerminalSessionId {
    val sessionId = TerminalSessionId(sessionIdCounter.getAndIncrement())
    sessionsMap[sessionId] = session
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

    @JvmStatic
    fun getInstance(project: Project): TerminalSessionsManager = project.service()
  }
}

@ApiStatus.Internal
data class TerminalSessionStartResult(
  val session: TerminalSession,
  val configuredOptions: ShellStartupOptions,
  val sessionId: TerminalSessionId,
  val ttyConnector: ObservableTtyConnector,
)
