// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.TerminalUiSettingsManager
import org.jetbrains.annotations.Nls

@State(name = "TerminalOptionsProvider", presentableName = TerminalOptionsProvider.PresentableNameGetter::class,
       storages = [(Storage("terminal.xml", roamingType = RoamingType.DEFAULT))])
class TerminalOptionsProvider : PersistentStateComponent<TerminalOptionsProvider.State> {
  private var myState = State()

  override fun getState(): State {
    return myState
  }

  override fun loadState(state: State) {
    myState = state
  }

  fun closeSessionOnLogout(): Boolean {
    return myState.myCloseSessionOnLogout
  }

  fun enableMouseReporting(): Boolean {
    return myState.myReportMouse
  }

  fun audibleBell(): Boolean {
    return myState.mySoundBell
  }

  var tabName: String
    @Nls
    get() : String = myState.myTabName ?: TerminalBundle.message("local.terminal.default.name")
    set(@Nls tabName) {
      myState.myTabName = tabName
    }

  fun overrideIdeShortcuts(): Boolean {
    return myState.myOverrideIdeShortcuts
  }

  fun setOverrideIdeShortcuts(overrideIdeShortcuts: Boolean) {
    myState.myOverrideIdeShortcuts = overrideIdeShortcuts
  }

  fun shellIntegration(): Boolean {
    return myState.myShellIntegration
  }

  fun setShellIntegration(shellIntegration: Boolean) {
    myState.myShellIntegration = shellIntegration
  }

  class State {
    var myShellPath: String? = null
    @Nls
    var myTabName: String? = null
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

  fun setCloseSessionOnLogout(closeSessionOnLogout: Boolean) {
    myState.myCloseSessionOnLogout = closeSessionOnLogout
  }

  fun setReportMouse(reportMouse: Boolean) {
    myState.myReportMouse = reportMouse
  }

  fun setSoundBell(soundBell: Boolean) {
    myState.mySoundBell = soundBell
  }

  fun copyOnSelection(): Boolean {
    return myState.myCopyOnSelection
  }

  fun setCopyOnSelection(copyOnSelection: Boolean) {
    myState.myCopyOnSelection = copyOnSelection
  }

  fun pasteOnMiddleMouseButton(): Boolean {
    return myState.myPasteOnMiddleMouseButton
  }

  fun setPasteOnMiddleMouseButton(pasteOnMiddleMouseButton: Boolean) {
    myState.myPasteOnMiddleMouseButton = pasteOnMiddleMouseButton
  }

  fun highlightHyperlinks(): Boolean {
    return myState.myHighlightHyperlinks
  }

  fun setHighlightHyperlinks(highlight: Boolean) {
    myState.myHighlightHyperlinks = highlight
  }

  @Deprecated("To be removed", ReplaceWith("org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider.getEnvData"))
  fun getEnvData(): EnvironmentVariablesData {
    return EnvironmentVariablesData.DEFAULT
  }

  @Deprecated("To be removed", ReplaceWith("org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider.setEnvData"))
  fun setEnvData(envData: EnvironmentVariablesData) {
  }

  // Or replace with `var shellPath: String? by myState::myShellPath`, but `myState` must be `val` in this case
  var shellPath: String?
    get() = myState.myShellPath
    set(value) {
      myState.myShellPath = value
    }

  var useOptionAsMetaKey: Boolean
    get() = myState.useOptionAsMetaKey
    set(value) {
      myState.useOptionAsMetaKey = value
    }

  var cursorShape: TerminalUiSettingsManager.CursorShape
    get() = service<TerminalUiSettingsManager>().cursorShape
    set(value) {
      service<TerminalUiSettingsManager>().cursorShape = value
    }

  companion object {
    val instance: TerminalOptionsProvider
      @JvmStatic
      get() = ApplicationManager.getApplication().getService(TerminalOptionsProvider::class.java)
  }

  class PresentableNameGetter: com.intellij.openapi.components.State.NameGetter() {
    override fun get(): String = TerminalBundle.message("toolwindow.stripe.Terminal")
  }
}
