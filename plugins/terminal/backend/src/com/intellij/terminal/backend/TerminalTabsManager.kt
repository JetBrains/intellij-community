package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.backend.delete
import com.intellij.platform.kernel.backend.newValueEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.PROJECT)
internal class TerminalTabsManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  suspend fun startTerminalSession(options: ShellStartupOptions): TerminalSessionId {
    val scope = coroutineScope.childScope("TerminalSession")
    val session = startTerminalSession(project, options, JBTerminalSystemSettingsProvider(), scope)

    val sessionEntity = newValueEntity(session)
    scope.awaitCancellationAndInvoke {
      sessionEntity.delete()
    }

    return TerminalSessionId(sessionEntity.id)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalTabsManager {
      return project.service()
    }
  }
}