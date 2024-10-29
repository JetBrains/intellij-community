// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.XMap

/**
 * Note, that this class is about block terminal usage.
 */
@Service
@State(name = "BlockTerminalUsage", storages = [Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED)])
internal class TerminalUsageLocalStorage : PersistentStateComponent<TerminalUsageLocalStorage.State> {
  private var state = State()

  val executedCommandsNumber: Int
    get() = state.shellToExecutedCommandsNumber.values.sum()

  /** Can be null only if [executedCommandsNumber] is zero */
  val mostUsedShell: String?
    get() = state.shellToExecutedCommandsNumber.keys.maxByOrNull { state.shellToExecutedCommandsNumber[it]!! }

  fun recordCommandExecuted(shellName: String) {
    state.shellToExecutedCommandsNumber.merge(shellName.lowercase(), 1, Int::plus)
  }

  fun recordBlockTerminalUsed() {
    val curVersionString = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
    state.blockTerminalUsedLastVersion = curVersionString
    state.blockTerminalUsedLastTimeMillis = System.currentTimeMillis()
  }

  fun recordBlockTerminalDisabled() {
    state.blockTerminalDisabledLastTimeMillis = System.currentTimeMillis()
  }

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  class State {
    @get:XMap
    val shellToExecutedCommandsNumber: MutableMap<String, Int> = HashMap()
    var feedbackNotificationShown: Boolean = false
    var blockTerminalUsedLastVersion: String? = null
    var blockTerminalUsedLastTimeMillis: Long = 0
    var blockTerminalDisabledLastTimeMillis: Long = 0
  }

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUsageLocalStorage = service()
  }
}
