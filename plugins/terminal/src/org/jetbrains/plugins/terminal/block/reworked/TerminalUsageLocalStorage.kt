// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus

/**
 * Note, that this class is about reworked terminal usage.
 */
@ApiStatus.Internal
@Service
@State(name = "ReworkedTerminalUsage", storages = [Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED)])
class TerminalUsageLocalStorage : PersistentStateComponent<TerminalUsageLocalStorage.State> {
  private var state = State()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  class State {
    var feedbackNotificationShown: Boolean = false
    var enterKeyPressedTimes: Int = 0
  }

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUsageLocalStorage = service()
  }
}
