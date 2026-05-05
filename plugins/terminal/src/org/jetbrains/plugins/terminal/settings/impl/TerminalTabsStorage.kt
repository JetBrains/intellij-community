package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "TerminalTabsStorage", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class TerminalTabsStorage : PersistentStateComponent<TerminalTabsStorage.State> {
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