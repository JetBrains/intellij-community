// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Note, that this class is about reworked terminal usage.
 */
@ApiStatus.Internal
@Service
@State(name = "ReworkedTerminalUsage", storages = [Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED)])
class TerminalUsageLocalStorage : PersistentStateComponent<TerminalUsageLocalStorage.State> {
  private val feedbackNotificationShown = AtomicBoolean()
  private val enterKeyPressedTimes = AtomicInteger()

  override fun getState(): State = State(
    feedbackNotificationShown.get(),
    enterKeyPressedTimes.get(),
  )

  override fun loadState(state: State) {
    feedbackNotificationShown.set(state.feedbackNotificationShown)
    enterKeyPressedTimes.set(state.enterKeyPressedTimes)
  }

  fun recordFeedbackNotificationShown() {
    feedbackNotificationShown.set(true)
  }

  fun recordEnterKeyPressed() {
    enterKeyPressedTimes.incrementAndGet()
  }

  @Serializable
  data class State(
    val feedbackNotificationShown: Boolean = false,
    val enterKeyPressedTimes: Int = 0,
  )

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUsageLocalStorage = service()
  }
}
