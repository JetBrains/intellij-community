// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.Nls

@State(name = "TerminalOptionsProvider", storages = [(Storage("terminal.xml"))])
class TerminalOptionsProvider : PersistentStateComponent<TerminalOptionsProvider.State> {
  private var myState = State()

  override fun getState(): State? {
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
    @get:Property(surroundWithTag = false, flat = true)
    var envDataOptions = EnvironmentVariablesDataOptions()
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

  fun getEnvData(): EnvironmentVariablesData {
    return myState.envDataOptions.get()
  }

  fun setEnvData(envData: EnvironmentVariablesData) {
    myState.envDataOptions.set(envData)
  }

  // replace with property delegate when Kotlin 1.4 arrives (KT-8658)
  var shellPath: String?
    get() = myState.myShellPath
    set(value) {
      myState.myShellPath = value
    }

  companion object {
    val instance: TerminalOptionsProvider
      @JvmStatic
      get() = ApplicationManager.getApplication().getService(TerminalOptionsProvider::class.java)
  }
}
