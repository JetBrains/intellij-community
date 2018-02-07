/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.terminal

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * @author traff
 */

@State(name = "TerminalOptionsProvider", storages = arrayOf(Storage("terminal.xml")))
class TerminalOptionsProvider : PersistentStateComponent<TerminalOptionsProvider.State> {
  private val myState = State()

  var shellPath: String? by ValueWithDefault(State::myShellPath, myState) { defaultShellPath }

  override fun getState(): State? {
    return myState
  }

  override fun loadState(state: State) {
    myState.myCloseSessionOnLogout = state.myCloseSessionOnLogout
    myState.myReportMouse = state.myReportMouse
    myState.mySoundBell = state.mySoundBell
    myState.myTabName = state.myTabName
    myState.myCopyOnSelection = state.myCopyOnSelection
    myState.myPasteOnMiddleMouseButton = state.myPasteOnMiddleMouseButton
    myState.myOverrideIdeShortcuts = state.myOverrideIdeShortcuts
    myState.myShellIntegration = state.myShellIntegration
    myState.myShellPath = state.myShellPath
    myState.myHighlightHyperlinks = state.myHighlightHyperlinks
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
    get() = myState.myTabName
    set(tabName) {
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
    var myTabName = "Local"
    var myCloseSessionOnLogout = true
    var myReportMouse = true
    var mySoundBell = true
    var myCopyOnSelection = true
    var myPasteOnMiddleMouseButton = true
    var myOverrideIdeShortcuts = true
    var myShellIntegration = true
    var myHighlightHyperlinks = true
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
    return myState.myHighlightHyperlinks;
  }

  fun setHighlightHyperlinks(highlight: Boolean) {
    myState.myHighlightHyperlinks = highlight
  }

  val defaultShellPath: String
    get() {
      val shell = System.getenv("SHELL")

      if (shell != null && File(shell).canExecute()) {
        return shell
      }

      if (SystemInfo.isUnix) {
        if (File("/bin/bash").exists()) {
          return "/bin/bash"
        }
        else {
          return "/bin/sh"
        }
      }
      else {
        return "cmd.exe"
      }
    }

  companion object {
    val instance: TerminalOptionsProvider
      get() = ServiceManager.getService(TerminalOptionsProvider::class.java)
  }
}





