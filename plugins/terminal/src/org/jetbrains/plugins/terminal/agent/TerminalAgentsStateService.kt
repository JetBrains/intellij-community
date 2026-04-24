// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.agent

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(
  name = "TerminalAgentsState",
  storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)],
)
class TerminalAgentsStateService : SerializablePersistentStateComponent<TerminalAgentsStateService.State>(State()) {
  var isSelectorVisible: Boolean
    get() = state.isSelectorVisible
    set(value) {
      if (value == state.isSelectorVisible) return
      updateState { it.copy(isSelectorVisible = value) }
      ActivityTracker.getInstance().inc()
    }

  var lastLaunchedAgentKey: TerminalAgent.AgentKey?
    get() = state.lastLaunchedAgentId?.let { TerminalAgent.AgentKey(it) }
    set(value) {
      if (value?.key == state.lastLaunchedAgentId) return
      updateState { it.copy(lastLaunchedAgentId = value?.key) }
      ActivityTracker.getInstance().inc()
    }

  fun consumeJunieNewBadgePresentation(): Boolean {
    val remainingPresentations = state.remainingJunieNewBadgePresentations
    if (remainingPresentations <= 0) return false

    updateState { it.copy(remainingJunieNewBadgePresentations = remainingPresentations - 1) }
    return true
  }

  fun resetJunieNewBadgePresentations() {
    updateState { it.copy(remainingJunieNewBadgePresentations = INITIAL_JUNIE_NEW_BADGE_SHOW_COUNT) }
    ActivityTracker.getInstance().inc()
  }

  @Serializable
  data class State(
    @JvmField val isSelectorVisible: Boolean = true,
    @JvmField val lastLaunchedAgentId: String? = null,
    @JvmField val remainingJunieNewBadgePresentations: Int = INITIAL_JUNIE_NEW_BADGE_SHOW_COUNT,
  ) {
    val lastLaunchedAgentKey: TerminalAgent.AgentKey? by lazy {
      lastLaunchedAgentId?.let { TerminalAgent.AgentKey(it) }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): TerminalAgentsStateService = service()

    const val INITIAL_JUNIE_NEW_BADGE_SHOW_COUNT: Int = 3
  }
}