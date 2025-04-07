package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.backend.delete
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.platform.kernel.backend.newValueEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.session.TerminalCloseEvent
import com.intellij.terminal.session.TerminalSession
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
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

      val scope = coroutineScope.childScope("TerminalSession")
      val result = startTerminalSession(options, scope)

      val updatedTab = tab.copy(
        shellCommand = result.configuredOptions.shellCommand,
        sessionId = result.sessionId,
        portForwardingId = result.portForwardingId,
      )
      tabs[tabId] = updatedTab

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
        val session = sessionId.eid.findValueEntity<TerminalSession>()?.value
        if (session == null) {
          // If the session is already removed, it means that close event was already sent to the session.
          // It's coroutine scope cancellation is in progress: we already removed the entity, but still not removed the tab.
          // Return here, because the tab will be removed once we free the tabs' lock.
          return@updateTabsAndStore
        }
        // It should terminate the shell process, then cancel the coroutine scope,
        // and finally remove the tab in awaitCancellationAndInvoke body defined in the methods above.
        session.getInputChannel().send(TerminalCloseEvent)
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
   * Returns ID of started terminal session and final options used for session start.
   */
  private suspend fun startTerminalSession(options: ShellStartupOptions, scope: CoroutineScope): TerminalSessionStartResult {
    val termSize = options.initialTermSize ?: run {
      LOG.warn("No initial terminal size provided, using default 80x24. $options")
      TermSize(80, 24)
    }
    val optionsWithSize = options.builder().initialTermSize(termSize).build()

    val (ttyConnector, configuredOptions) = startTerminalProcess(project, optionsWithSize)
    val observableTtyConnector = ObservableTtyConnector(ttyConnector)
    val session = createTerminalSession(project, observableTtyConnector, termSize, JBTerminalSystemSettingsProvider(), scope)
    val stateAwareSession = StateAwareTerminalSession(session)

    val sessionEntity = newValueEntity(stateAwareSession)

    scope.awaitCancellationAndInvoke {
      sessionEntity.delete()
    }

    val portForwardingScope = scope.childScope("PortForwarding")
    val portForwardingId = TerminalPortForwardingManager.getInstance(project).setupPortForwarding(observableTtyConnector, portForwardingScope)

    return TerminalSessionStartResult(
      sessionId = TerminalSessionId(sessionEntity.id),
      configuredOptions,
      portForwardingId
    )
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
    )
  }

  private fun TerminalSessionPersistedTab.toSessionTab(): TerminalSessionTab {
    return TerminalSessionTab(
      id = tabIdCounter.andIncrement,
      name = name,
      isUserDefinedName = isUserDefinedName,
      shellCommand = shellCommand,
      sessionId = null,
      portForwardingId = null,
    )
  }

  private data class TerminalSessionStartResult(
    val sessionId: TerminalSessionId,
    val configuredOptions: ShellStartupOptions,
    val portForwardingId: TerminalPortForwardingId?,
  )

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalTabsManager {
      return project.service()
    }

    private val LOG: Logger = logger<TerminalTabsManager>()
  }
}