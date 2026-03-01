package com.intellij.terminal.backend

import com.intellij.ide.util.RunOnceUtil
import com.intellij.idea.AppMode
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@Service(Service.Level.PROJECT)
@State(name = "TerminalTabsStorage", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class TerminalTabsStorage(private val project: Project) : PersistentStateComponent<TerminalTabsStorage.State> {
  class State {
    @XCollection
    var tabs: List<TerminalSessionPersistedTab> = emptyList()
  }

  private var state = State()

  fun getStoredTabs(): List<TerminalSessionPersistedTab> {
    // Fill the initial state of the reworked terminal tabs storage from the TerminalArrangementManager (classic terminal tabs storage).
    RunOnceUtil.runOnceForProject(project, "TerminalTabsStorage.copyFrom.TerminalArrangementManager.252") {
      // Do not perform the migration in RemDev, since the Reworked terminal is already enabled there by default.
      if (AppMode.isRemoteDevHost()) return@runOnceForProject

      // Do not perform the migration if the user already has any stored Reworked Terminal tabs.
      if (state.tabs.isEmpty()) {
        restoreStateFromTerminalArrangementManager()
      }
    }

    return state.tabs
  }

  fun updateStoredTabs(tabs: List<TerminalSessionPersistedTab>) {
    state.tabs = tabs
  }

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
  }

  private fun restoreStateFromTerminalArrangementManager() {
    val tabsToCopy = TerminalArrangementManager.getInstance(project).arrangementState?.myTabStates ?: return

    val newTabs = mutableListOf<TerminalSessionPersistedTab>()
    for (tabToCopy: TerminalTabState in tabsToCopy) {
      val tab = TerminalSessionPersistedTab(
        name = tabToCopy.myTabName,
        isUserDefinedName = tabToCopy.myIsUserDefinedTabTitle,
        shellCommand = tabToCopy.myShellCommand,
        workingDirectory = tabToCopy.myWorkingDirectory,
        envVariables = null,
        processType = TerminalProcessType.SHELL,
      )
      newTabs.add(tab)
    }

    state.tabs = newTabs
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalTabsStorage {
      return project.service()
    }
  }
}