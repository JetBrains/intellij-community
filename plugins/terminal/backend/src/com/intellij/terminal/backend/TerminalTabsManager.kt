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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
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

  suspend fun createNewTerminalTab(options: ShellStartupOptions): TerminalSessionTab {
    val scope = coroutineScope.childScope("TerminalSession")
    val (sessionId, configuredOptions) = startTerminalSession(options, scope)

    val newTab = TerminalSessionTab(
      id = tabIdCounter.andIncrement,
      name = null,
      shellCommand = configuredOptions.shellCommand,
      sessionId = sessionId
    )
    updateTabsAndStore { tabs ->
      tabs[newTab.id] = newTab
    }

    scope.awaitCancellationAndInvoke {
      updateTabsAndStore { tabs ->
        tabs.remove(newTab.id)
      }
    }

    return newTab
  }

  suspend fun startTerminalSessionForTab(tabId: Int, options: ShellStartupOptions): TerminalSessionId {
    return updateTabsAndStore { tabs ->
      val tab = tabs[tabId] ?: error("No TerminalSessionTab with ID: $tabId")
      val existingSessionId = tab.sessionId
      if (existingSessionId != null) {
        return@updateTabsAndStore existingSessionId
      }

      val scope = coroutineScope.childScope("TerminalSession")
      val (sessionId, configuredOptions) = startTerminalSession(options, scope)

      val updatedTab = tab.copy(
        shellCommand = configuredOptions.shellCommand,
        sessionId = sessionId
      )
      tabs[tabId] = updatedTab

      scope.awaitCancellationAndInvoke {
        updateTabsAndStore { tabs ->
          tabs.remove(tabId)
        }
      }

      sessionId
    }
  }

  /**
   * Returns ID of started terminal session and final options used for session start.
   */
  private suspend fun startTerminalSession(options: ShellStartupOptions, scope: CoroutineScope): Pair<TerminalSessionId, ShellStartupOptions> {
    val (session, configuredOptions) = startTerminalSession(project, options, JBTerminalSystemSettingsProvider(), scope)
    val sessionEntity = newValueEntity(session)

    scope.awaitCancellationAndInvoke {
      sessionEntity.delete()
    }

    return TerminalSessionId(sessionEntity.id) to configuredOptions
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
      shellCommand = shellCommand,
    )
  }

  private fun TerminalSessionPersistedTab.toSessionTab(): TerminalSessionTab {
    return TerminalSessionTab(
      id = tabIdCounter.andIncrement,
      name = name,
      shellCommand = shellCommand,
      sessionId = null,
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalTabsManager {
      return project.service()
    }
  }
}