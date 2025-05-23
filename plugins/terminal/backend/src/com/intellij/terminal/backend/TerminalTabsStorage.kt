package com.intellij.terminal.backend

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager

@Service(Service.Level.PROJECT)
@State(name = "TerminalTabsStorage", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class TerminalTabsStorage(private val project: Project) : PersistentStateComponent<TerminalTabsStorage.State> {
  class State {
    @XCollection
    var tabs: List<TerminalSessionPersistedTab> = emptyList()
  }

  private var state = State()

  fun getStoredTabs(): List<TerminalSessionPersistedTab> {
    // Fill the initial state of the new terminal tabs storage from the TerminalArrangementManager.
    RunOnceUtil.runOnceForProject(project, "TerminalTabsStorage.copyFrom.TerminalArrangementManager") {
      restoreStateFromTerminalArrangementManager()
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