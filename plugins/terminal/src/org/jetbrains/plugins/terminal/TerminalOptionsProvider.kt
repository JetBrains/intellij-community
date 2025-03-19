// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.TerminalUiSettingsManager.CursorShape
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions
import org.jetbrains.plugins.terminal.settings.TerminalOsSpecificOptions
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("DEPRECATION")
@State(name = TerminalOptionsProvider.COMPONENT_NAME,
       category = SettingsCategory.TOOLS,
       exportable = true,
       presentableName = TerminalOptionsProvider.PresentableNameGetter::class,
       storages = [Storage(value = "terminal.xml")])
class TerminalOptionsProvider : PersistentStateComponent<TerminalOptionsProvider.State> {
  private var state = State()

  override fun getState(): State = state

  override fun loadState(newState: State) {
    state = newState

    RunOnceUtil.runOnceForApp("TerminalOptionsProvider.cursorShape.migration") {
      val previousCursorShape = TerminalUiSettingsManager.getInstance().cursorShape
      state.cursorShape = previousCursorShape
    }

    RunOnceUtil.runOnceForApp("TerminalOptionsProvider.terminalEngine.migration") {
      // The initial state of the terminal engine value should be composed out of registry values
      // used previously to determine what terminal to use.
      val isReworkedValue = Registry.`is`(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY)
      val isNewTerminalValue = Registry.`is`(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY)

      // Order of conditions is important!
      // New Terminal registry prevails, even if reworked registry is enabled.
      state.terminalEngine = when {
        isNewTerminalValue -> TerminalEngine.NEW_TERMINAL
        isReworkedValue -> TerminalEngine.REWORKED
        else -> TerminalEngine.CLASSIC
      }
    }

    // In the case of RemDev settings are synced from backend to frontend using `loadState` method.
    // So, notify the listeners on every `loadState` to not miss the change.
    fireSettingsChanged()
  }

  override fun noStateLoaded() {
    loadState(State())
  }

  class State {
    @ApiStatus.Internal
    var terminalEngine: TerminalEngine = TerminalEngine.CLASSIC

    var myTabName: @Nls String = TerminalBundle.message("local.terminal.default.name")
    var myCloseSessionOnLogout: Boolean = true
    var myReportMouse: Boolean = true
    var mySoundBell: Boolean = true
    var myPasteOnMiddleMouseButton: Boolean = true
    var myOverrideIdeShortcuts: Boolean = true
    var myShellIntegration: Boolean = true
    var myHighlightHyperlinks: Boolean = true
    var useOptionAsMetaKey: Boolean = false
    var cursorShape: CursorShape = CursorShape.BLOCK

    @Deprecated("Use BlockTerminalOptions#promptStyle instead")
    var useShellPrompt: Boolean = false

    @Deprecated("Use TerminalLocalOptions#shellPath instead", ReplaceWith("TerminalLocalOptions.getInstance().shellPath"))
    var myShellPath: String? = null

    @Deprecated("Use TerminalOsSpecificOptions#copyOnSelection instead", ReplaceWith("TerminalOsSpecificOptions.getInstance().copyOnSelection"))
    var myCopyOnSelection: Boolean = SystemInfo.isLinux
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

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var terminalEngine: TerminalEngine
    get() = state.terminalEngine
    set(value) {
      if (state.terminalEngine != value) {
        state.terminalEngine = value
        fireSettingsChanged()
      }
    }

  @Deprecated("Use TerminalLocalOptions#shellPath instead", ReplaceWith("TerminalLocalOptions.getInstance().shellPath"))
  var shellPath: String?
    get() = TerminalLocalOptions.getInstance().shellPath
    set(value) {
      val options = TerminalLocalOptions.getInstance()
      if (options.shellPath != value) {
        options.shellPath = value
        fireSettingsChanged()
      }
    }

  var tabName: @Nls String
    get() = state.myTabName
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

  @Deprecated("Use TerminalOsSpecificOptions#copyOnSelection instead", ReplaceWith("TerminalOsSpecificOptions.getInstance().copyOnSelection"))
  var copyOnSelection: Boolean
    get() = TerminalOsSpecificOptions.getInstance().copyOnSelection
    set(value) {
      val options = TerminalOsSpecificOptions.getInstance()
      if (options.copyOnSelection != value) {
        options.copyOnSelection = value
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

  @Deprecated("Use BlockTerminalOptions#promptStyle instead")
  var useShellPrompt: Boolean
    get() = state.useShellPrompt
    set(value) {
      if (state.useShellPrompt != value) {
        state.useShellPrompt = value
        fireSettingsChanged()
      }
    }

  var cursorShape: CursorShape
    get() = state.cursorShape
    set(value) {
      if (state.cursorShape != value) {
        state.cursorShape = value
        TerminalUiSettingsManager.getInstance().cursorShape = value
        fireSettingsChanged()
      }
    }

  companion object {
    val instance: TerminalOptionsProvider
      @JvmStatic
      get() = service()

    internal const val COMPONENT_NAME: String = "TerminalOptionsProvider"
  }

  class PresentableNameGetter: com.intellij.openapi.components.State.NameGetter() {
    override fun get(): String = TerminalBundle.message("toolwindow.stripe.Terminal")
  }
}
