package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.backend.delete
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.platform.kernel.backend.newValueEntity
import com.intellij.terminal.session.TerminalSession
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.APP)
internal class TerminalSessionsManager {
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
    val (session, configuredOptions) = startTerminalSession(project, options, JBTerminalSystemSettingsProvider(), scope)
    val stateAwareSession = StateAwareTerminalSession(session)

    val sessionEntity = newValueEntity(stateAwareSession)

    scope.awaitCancellationAndInvoke {
      sessionEntity.delete()
    }
    val sessionId = TerminalSessionId(sessionEntity.id)

    return TerminalSessionStartResult(
      configuredOptions,
      sessionId,
    )
  }

  suspend fun getSession(id: TerminalSessionId): TerminalSession? {
    return id.eid.findValueEntity<TerminalSession>()?.value
  }

  companion object {
    @JvmStatic
    fun getInstance(): TerminalSessionsManager = service()
  }
}

internal data class TerminalSessionStartResult(
  val configuredOptions: ShellStartupOptions,
  val sessionId: TerminalSessionId,
)