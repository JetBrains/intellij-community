// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalFirstIdeSessionMoment
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
@Service
@State(
  name = "ReworkedTerminalUsage",
  storages = [
    Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED),
    Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  ]
)
class TerminalUsageLocalStorage : PersistentStateComponent<TerminalUsageLocalStorage.State> {
  private val feedbackNotificationShown = AtomicBoolean()
  private val enterKeyPressedTimes = AtomicInteger()

  private val completionFeedbackNotificationShown = AtomicBoolean()
  private val completionPopupShownTimes = AtomicInteger()
  private val completionItemChosenTimes = AtomicInteger()

  private val firstIdeSessionMoment = AtomicReference<TerminalFirstIdeSessionMoment?>(null)

  override fun getState(): State = State(
    feedbackNotificationShown.get(),
    enterKeyPressedTimes.get(),
    completionFeedbackNotificationShown.get(),
    completionPopupShownTimes.get(),
    completionItemChosenTimes.get(),
    firstIdeSessionMoment.get(),
  )

  override fun loadState(state: State) {
    feedbackNotificationShown.set(state.feedbackNotificationShown)
    enterKeyPressedTimes.set(state.enterKeyPressedTimes)
    completionFeedbackNotificationShown.set(state.completionFeedbackNotificationShown)
    completionPopupShownTimes.set(state.completionPopupShownTimes)
    completionItemChosenTimes.set(state.completionItemChosenTimes)
    firstIdeSessionMoment.set(state.firstIdeSessionMoment)
  }

  fun recordFeedbackNotificationShown() {
    feedbackNotificationShown.set(true)
  }

  fun recordEnterKeyPressed() {
    enterKeyPressedTimes.incrementAndGet()
  }

  fun recordCompletionFeedbackNotificationShown() {
    completionFeedbackNotificationShown.set(true)
  }

  fun recordCompletionPopupShown() {
    completionPopupShownTimes.incrementAndGet()
  }

  fun recordCompletionItemChosen() {
    completionItemChosenTimes.incrementAndGet()
  }

  fun recordFirstIdeSessionMoment(moment: TerminalFirstIdeSessionMoment) {
    firstIdeSessionMoment.set(moment)
  }

  @Serializable
  data class State(
    val feedbackNotificationShown: Boolean = false,
    val enterKeyPressedTimes: Int = 0,
    val completionFeedbackNotificationShown: Boolean = false,
    val completionPopupShownTimes: Int = 0,
    val completionItemChosenTimes: Int = 0,
    val firstIdeSessionMoment: TerminalFirstIdeSessionMoment? = null,
  )

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUsageLocalStorage = service()
  }
}
