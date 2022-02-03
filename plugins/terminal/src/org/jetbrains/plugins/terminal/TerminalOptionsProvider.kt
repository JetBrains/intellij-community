// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.TerminalUiSettingsManager
import org.jetbrains.annotations.Nls

@State(name = "TerminalOptionsProvider", presentableName = TerminalOptionsProvider.PresentableNameGetter::class,
       storages = [Storage("terminal.xml")])
class TerminalOptionsProvider : PersistentStateComponent<TerminalOptionsProvider.State> {
  private var state = State()

  override fun getState(): State = state

  override fun loadState(newState: State) {
    state = newState
  }

  class State {
    var myShellPath: String? = null
    var myTabName: @Nls String? = null
    var myCloseSessionOnLogout: Boolean = true
    var myReportMouse: Boolean = true
    var mySoundBell: Boolean = true
    var myCopyOnSelection: Boolean = SystemInfo.isLinux
    var myPasteOnMiddleMouseButton: Boolean = true
    var myOverrideIdeShortcuts: Boolean = true
    var myShellIntegration: Boolean = true
    var myHighlightHyperlinks: Boolean = true
    var useOptionAsMetaKey: Boolean = false
  }

  // Nice property delegation (var shellPath: String? by state::myShellPath) cannot be used on `var` properties (KTIJ-19450)
  var shellPath: String?
    get() = state.myShellPath
    set(value) {
      state.myShellPath = value
    }

  var tabName: @Nls String
    get() = state.myTabName ?: TerminalBundle.message("local.terminal.default.name")
    set(@Nls tabName) {
      state.myTabName = tabName
    }

  var closeSessionOnLogout: Boolean
    get() = state.myCloseSessionOnLogout
    set(value) {
      state.myCloseSessionOnLogout = value
    }

  var mouseReporting: Boolean
    get() = state.myReportMouse
    set(value) {
      state.myReportMouse = value
    }

  var audibleBell: Boolean
    get() = state.mySoundBell
    set(value) {
      state.mySoundBell = value
    }

  var copyOnSelection: Boolean
    get() = state.myCopyOnSelection
    set(value) {
      state.myCopyOnSelection = value
    }

  var pasteOnMiddleMouseButton: Boolean
    get() = state.myPasteOnMiddleMouseButton
    set(value) {
      state.myPasteOnMiddleMouseButton = value
    }

  var overrideIdeShortcuts: Boolean
    get() = state.myOverrideIdeShortcuts
    set(value) {
      state.myOverrideIdeShortcuts = value
    }

  var shellIntegration: Boolean
    get() = state.myShellIntegration
    set(value) {
      state.myShellIntegration = value
    }

  var highlightHyperlinks: Boolean
    get() = state.myHighlightHyperlinks
    set(value) {
      state.myHighlightHyperlinks = value
    }

  var useOptionAsMetaKey: Boolean
    get() = state.useOptionAsMetaKey
    set(value) {
      state.useOptionAsMetaKey = value
    }

  var cursorShape: TerminalUiSettingsManager.CursorShape
    get() = service<TerminalUiSettingsManager>().cursorShape
    set(value) {
      service<TerminalUiSettingsManager>().cursorShape = value
    }

  @Deprecated("To be removed", ReplaceWith("org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider.setEnvData"))
  fun setEnvData(@Suppress("UNUSED_PARAMETER") envData: EnvironmentVariablesData) {
  }

  companion object {
    val instance: TerminalOptionsProvider
      @JvmStatic
      get() = service()
  }

  class PresentableNameGetter: com.intellij.openapi.components.State.NameGetter() {
    override fun get(): String = TerminalBundle.message("toolwindow.stripe.Terminal")
  }
}
