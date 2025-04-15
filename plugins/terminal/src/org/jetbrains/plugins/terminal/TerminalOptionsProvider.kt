// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.ide.util.RunOnceUtil
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.TerminalUiSettingsManager.CursorShape
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("DEPRECATION")
@State(name = TerminalOptionsProvider.COMPONENT_NAME,
       category = SettingsCategory.TOOLS,
       exportable = true,
       presentableName = TerminalOptionsProvider.PresentableNameGetter::class,
       storages = [Storage(value = "terminal.xml")])
class TerminalOptionsProvider(private val coroutineScope: CoroutineScope) : PersistentStateComponent<TerminalOptionsProvider.State> {
  private var state = State()

  override fun getState(): State = state

  override fun loadState(newState: State) {
    state = newState

    RunOnceUtil.runOnceForApp("TerminalOptionsProvider.cursorShape.migration") {
      val previousCursorShape = TerminalUiSettingsManager.getInstance().cursorShape
      state.cursorShape = previousCursorShape
    }

    RunOnceUtil.runOnceForApp("TerminalOptionsProvider.terminalEngine.migration") {
      // If migration is happened in IDE backend, let's skip it.
      // Because we should receive the correct terminal engine value from the frontend and use it.
      // Otherwise, there can be a race when migration is performed both on backend and frontend simultaneously.
      if (AppMode.isRemoteDevHost()) return@runOnceForApp

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

      thisLogger().info("Initialized TerminalOptionsProvider.terminalEngine value from registry to ${state.terminalEngine}")

      // Trigger sending the updated terminal engine value to the backend
      coroutineScope.launch {
        saveSettingsForRemoteDevelopment(application)
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
    var myCopyOnSelection: Boolean = false
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

  /**
   * Enables emulation of Linux-like system selection clipboard behavior on Windows and macOS.
   * Makes no sense on Linux, because system selection clipboard is enabled by default there.
   */
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
