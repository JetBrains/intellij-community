package com.intellij.terminal.backend

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection

@Service(Service.Level.PROJECT)
@State(name = "TerminalTabsStorage", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class TerminalTabsStorage : PersistentStateComponent<TerminalTabsStorage.State> {
  class State {
    @XCollection
    var tabs: List<TerminalSessionPersistedTab> = emptyList()
  }

  private var state = State()

  fun getStoredTabs(): List<TerminalSessionPersistedTab> {
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

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalTabsStorage {
      return project.service()
    }
  }
}