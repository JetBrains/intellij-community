// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.TerminalUiSettingsManager
import org.jetbrains.annotations.Nls
import java.util.concurrent.CopyOnWriteArrayList

@State(name = "TerminalOptionsProvider",
       category = SettingsCategory.TOOLS,
       exportable = true,
       presentableName = TerminalOptionsProvider.PresentableNameGetter::class,
       storages = [Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED)])
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
    var useShellPrompt: Boolean = false
  }

  private val listeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  fun addListener(disposable: Disposable, listener: () -> Unit) {
    TerminalUtil.addItem(listeners, listener, disposable)
  }

  private fun fireSettingsChanged() {
    for (listener in listeners) {
      listener()
    }
  }

  // Nice property delegation (var shellPath: String? by state::myShellPath) cannot be used on `var` properties (KTIJ-19450)
  var shellPath: String?
    get() = state.myShellPath
    set(value) {
      if (state.myShellPath != value) {
        state.myShellPath = value
        fireSettingsChanged()
      }
    }

  var tabName: @Nls String
    get() = state.myTabName ?: TerminalBundle.message("local.terminal.default.name")
    set(@Nls tabName) {
      if (state.myTabName != tabName) {
        state.myTabName = tabName
        fireSettingsChanged()
      }
    }

  var closeSessionOnLogout: Boolean
    get() = state.myCloseSessionOnLogout
    set(value) {
      if (state.myCloseSessionOnLogout != value) {
        state.myCloseSessionOnLogout = value
        fireSettingsChanged()
      }
    }

  var mouseReporting: Boolean
    get() = state.myReportMouse
    set(value) {
      if (state.myReportMouse != value) {
        state.myReportMouse = value
        fireSettingsChanged()
      }
    }

  var audibleBell: Boolean
    get() = state.mySoundBell
    set(value) {
      if (state.mySoundBell != value) {
        state.mySoundBell = value
        fireSettingsChanged()
      }
    }

  var copyOnSelection: Boolean
    get() = state.myCopyOnSelection
    set(value) {
      if (state.myCopyOnSelection != value) {
        state.myCopyOnSelection = value
        fireSettingsChanged()
      }
    }

  var pasteOnMiddleMouseButton: Boolean
    get() = state.myPasteOnMiddleMouseButton
    set(value) {
      if (state.myPasteOnMiddleMouseButton != value) {
        state.myPasteOnMiddleMouseButton = value
        fireSettingsChanged()
      }
    }

  var overrideIdeShortcuts: Boolean
    get() = state.myOverrideIdeShortcuts
    set(value) {
      if (state.myOverrideIdeShortcuts != value) {
        state.myOverrideIdeShortcuts = value
        fireSettingsChanged()
      }
    }

  var shellIntegration: Boolean
    get() = state.myShellIntegration
    set(value) {
      if (state.myShellIntegration != value) {
        state.myShellIntegration = value
        fireSettingsChanged()
      }
    }

  var highlightHyperlinks: Boolean
    get() = state.myHighlightHyperlinks
    set(value) {
      if (state.myHighlightHyperlinks != value) {
        state.myHighlightHyperlinks = value
        fireSettingsChanged()
      }
    }

  var useOptionAsMetaKey: Boolean
    get() = state.useOptionAsMetaKey
    set(value) {
      if (state.useOptionAsMetaKey != value) {
        state.useOptionAsMetaKey = value
        fireSettingsChanged()
      }
    }

  var useShellPrompt: Boolean
    get() = state.useShellPrompt
    set(value) {
      if (state.useShellPrompt != value) {
        state.useShellPrompt = value
        fireSettingsChanged()
      }
    }

  var cursorShape: TerminalUiSettingsManager.CursorShape
    get() = TerminalUiSettingsManager.getInstance().cursorShape
    set(value) {
      val uiSettings = TerminalUiSettingsManager.getInstance()
      if (uiSettings.cursorShape != value) {
        uiSettings.cursorShape = value
        fireSettingsChanged()
      }
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
