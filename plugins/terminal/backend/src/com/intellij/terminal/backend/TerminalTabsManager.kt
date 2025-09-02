package com.intellij.terminal.backend

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientIdContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.session.TerminalCloseEvent
import com.intellij.terminal.session.TerminalStateChangedEvent
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
import java.util.concurrent.atomic.AtomicInteger

@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.PROJECT)
internal class TerminalTabsManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val tabsMap: MutableMap<Int, TerminalSessionTab> = LinkedHashMap()
  private val tabsLock = Mutex()
  private val tabIdCounter = AtomicInteger(0)

  init {
    val storedTabs = TerminalTabsStorage.getInstance(project).getStoredTabs()
    for (tab in storedTabs) {
      val sessionTab = tab.toSessionTab()
      tabsMap[sessionTab.id] = sessionTab
    }
  }

  suspend fun getTerminalTabs(): List<TerminalSessionTab> {
    return tabsLock.withLock {
      tabsMap.values.toList()
    }
  }

  suspend fun createNewTerminalTab(): TerminalSessionTab {
    val newTab = TerminalSessionTab(
      id = tabIdCounter.andIncrement,
      name = null,
      isUserDefinedName = false,
      shellCommand = null,
      workingDirectory = null,
      sessionId = null,
      portForwardingId = null,
    )
    updateTabsAndStore { tabs ->
      tabs[newTab.id] = newTab
    }

    return newTab
  }

  suspend fun startTerminalSessionForTab(tabId: Int, options: ShellStartupOptions): TerminalSessionTab {
    return updateTabsAndStore { tabs ->
      val tab = tabs[tabId] ?: error("No TerminalSessionTab with ID: $tabId")
      val existingSessionId = tab.sessionId
      if (existingSessionId != null) {
        return@updateTabsAndStore tab
      }

      // Create and emulate the terminal session under the local client ID.
      // Because the session should be left active after the client disconnects.
      val clientId = ClientId.localId
      val scope = coroutineScope.childScope("TerminalSession#${tabId}", ClientIdContextElement(clientId))
      val result = withContext(ClientIdContextElement(clientId)) {
        TerminalSessionsManager.getInstance().startSession(options, project, scope)
      }

      // But init Port Forwarding session under the remote client ID, because Port Forwarding API relies on it.
      val portForwardingId = TerminalPortForwardingManager.getInstance(project).setupPortForwarding(
        ttyConnector = result.ttyConnector,
        coroutineScope = scope.childScope("PortForwarding")
      )

      val updatedTab = tab.copy(
        shellCommand = options.shellCommand,
        workingDirectory = result.configuredOptions.workingDirectory,
        sessionId = result.sessionId,
        portForwardingId = portForwardingId,
      )
      tabs[tabId] = updatedTab

      trackWorkingDirectory(updatedTab, scope.childScope("Working directory tracking"))

      scope.awaitCancellationAndInvoke {
        updateTabsAndStore { tabs ->
          tabs.remove(tabId)
        }
      }

      updatedTab
    }
  }

  suspend fun closeTerminalTab(tabId: Int) {
    updateTabsAndStore { tabs ->
      val tab = tabs[tabId] ?: return@updateTabsAndStore  // Already removed or never existed
      val sessionId = tab.sessionId
      if (sessionId != null) {
        val session = TerminalSessionsManager.getInstance().getSession(sessionId)
        if (session == null) {
          // If the session is already removed, it means that close event was already sent to the session.
          // It's coroutine scope cancellation is in progress: we already removed the entity, but still not removed the tab.
          // Return here, because the tab will be removed once we free the tabs' lock.
          return@updateTabsAndStore
        }
        // It should terminate the shell process, then cancel the coroutine scope,
        // and finally remove the tab in awaitCancellationAndInvoke body defined in the methods above.
        session.getInputChannel().send(TerminalCloseEvent())
      }
      else {
        // The session was not started - just remove the tab.
        tabs.remove(tabId)
      }
    }
  }

  suspend fun renameTerminalTab(tabId: Int, newName: String, isUserDefinedName: Boolean) {
    updateTabsAndStore { tabs ->
      val tab = tabs[tabId] ?: return@updateTabsAndStore
      val updatedTab = tab.copy(name = newName, isUserDefinedName = isUserDefinedName)
      tabs[tabId] = updatedTab
    }
  }

  /**
   * Updates the [TerminalSessionTab.workingDirectory] field of the given [tab]
   * once the working directory is changed in the started terminal session.
   * So, the working directory is persisted in the [TerminalTabsStorage]
   * and can be used to start the new session on the next IDE launch.
   */
  private fun trackWorkingDirectory(tab: TerminalSessionTab, coroutineScope: CoroutineScope) {
    val sessionId = tab.sessionId ?: error("This method should be called only for tabs with started sessions: $tab")
    val session = TerminalSessionsManager.getInstance().getSession(sessionId) ?: error("No session for tab $tab")

    coroutineScope.launch {
      val outputFlow = session.getOutputFlow()

      var currentDirectory: String? = tab.workingDirectory
      outputFlow.collect { events ->
        for (event in events) {
          if (event is TerminalStateChangedEvent && event.state.currentDirectory != currentDirectory) {
            currentDirectory = event.state.currentDirectory
            updateTabsAndStore { tabs ->
              val updatedTab = tabs[tab.id]?.copy(workingDirectory = currentDirectory) ?: return@updateTabsAndStore
              tabs[tab.id] = updatedTab
            }
          }
        }
      }
    }
  }

  private suspend fun <T> updateTabsAndStore(action: suspend (MutableMap<Int, TerminalSessionTab>) -> T): T {
    return tabsLock.withLock {
      try {
        action(tabsMap)
      }
      finally {
        val persistedTabs = tabsMap.values.map { it.toPersistedTab() }
        TerminalTabsStorage.getInstance(project).updateStoredTabs(persistedTabs)
      }
    }
  }

  private fun TerminalSessionTab.toPersistedTab(): TerminalSessionPersistedTab {
    return TerminalSessionPersistedTab(
      name = name,
      isUserDefinedName = isUserDefinedName,
      shellCommand = shellCommand,
      workingDirectory = workingDirectory,
    )
  }

  private fun TerminalSessionPersistedTab.toSessionTab(): TerminalSessionTab {
    return TerminalSessionTab(
      id = tabIdCounter.andIncrement,
      name = name,
      isUserDefinedName = isUserDefinedName,
      shellCommand = shellCommand,
      workingDirectory = workingDirectory,
      sessionId = null,
      portForwardingId = null,
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalTabsManager {
      return project.service()
    }

    private val LOG: Logger = logger<TerminalTabsManager>()
  }
}