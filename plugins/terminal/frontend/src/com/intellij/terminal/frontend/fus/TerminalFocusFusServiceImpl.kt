@file:OptIn(FlowPreview::class)

package com.intellij.terminal.frontend.fus

import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalFocusFusService
import org.jetbrains.plugins.terminal.fus.TerminalNonToolWindowFocus
import java.awt.AWTEvent.FOCUS_EVENT_MASK
import java.awt.AWTEvent.WINDOW_FOCUS_EVENT_MASK
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.FocusEvent
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean

internal class TerminalFocusFusServiceImpl(private val coroutineScope: CoroutineScope) : TerminalFocusFusService {

  private val initialized = AtomicBoolean(false)

  private val stateFlow = MutableStateFlow<FocusedComponent>(InitialState)

  private var focusedWindow: Window? = null
    set(value) {
      field = value
      updateState()
    }

  private var focusedComponent: Component? = null
    set(value) {
      field = value
      updateState()
    }

  private fun updateState() {
    stateFlow.value = computeCurrentState()
  }

  private fun computeCurrentState(): FocusedComponent {
    val focusedComponent = this.focusedComponent
    val focusedWindow = this.focusedWindow
    return when {
      focusedComponent == null || focusedWindow == null -> {
        FocusedNonToolWindow(TerminalNonToolWindowFocus.OTHER_APPLICATION)
      }
      ComponentUtil.getParentOfType(TerminalPanelMarker::class.java, focusedComponent) != null -> {
        FocusedTerminal
      }
      ComponentUtil.getParentOfType(EditorsSplitters::class.java, focusedComponent) != null -> {
        FocusedNonToolWindow(TerminalNonToolWindowFocus.EDITOR)
      }
      else -> {
        val id = ComponentUtil.getParentOfType(InternalDecoratorImpl::class.java, focusedComponent)?.toolWindowId
        if (id == TerminalToolWindowFactory.TOOL_WINDOW_ID) {
          // Could only be possible if the terminal tool window contains a focusable component that is not the terminal itself.
          // But just in case let's check.
          FocusedTerminal
        }
        else if (id != null) {
          FocusedToolWindow(id)
        }
        else {
          FocusedNonToolWindow(TerminalNonToolWindowFocus.OTHER_COMPONENT)
        }
      }
    }
  }

  override fun ensureInitialized() {
    if (!initialized.compareAndSet(false, true)) return
    coroutineScope.launch(Dispatchers.UI + CoroutineName("TerminalFocusFusService initialization")) {
      initializeState()
      installAWTListener()
    }
    coroutineScope.launch(CoroutineName("TerminalFocusFusService state collector")) {
      collectState()
    }
  }

  private fun initializeState() {
    focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    focusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
  }

  private fun installAWTListener() {
    val listener = AWTEventListener { event ->
      when (event.id) {
        WindowEvent.WINDOW_GAINED_FOCUS -> focusedWindow = event.source as? Window?
        WindowEvent.WINDOW_LOST_FOCUS -> focusedWindow = null
        FocusEvent.FOCUS_GAINED -> focusedComponent = event.source as? Component
      }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, FOCUS_EVENT_MASK or WINDOW_FOCUS_EVENT_MASK)
    // Some defensive coding here. We could've passed coroutineScope.asDisposable(),
    // but then the references will continue pointing to the last components stored there.
    // It shouldn't be an issue because the scope is only canceled when the entire service is disposed,
    // but just in case the service leaks somewhere, let's make sure that the references are set to null anyway.
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
      focusedWindow = null
      focusedComponent = null
    }
  }

  private suspend fun collectState() {
    var previousState: FocusedComponent = InitialState
    stateFlow.debounce(500).distinctUntilChanged().collectLatest { state ->
      if (!previousState.isTerminalFocused && state.isTerminalFocused) {
        logFocusEnteringTerminal(previousState)
      }
      else if (previousState.isTerminalFocused && !state.isTerminalFocused) {
        logFocusLeavingTerminal(state)
      }
      previousState = state
    }
  }

  private fun logFocusEnteringTerminal(previousState: FocusedComponent) {
    val fusString = previousState.toFusString()
    if (fusString == null) {
      LOG.debug("Not logging the terminal focus event because it's the first focused component")
      return
    }
    LOG.debug("The terminal gained focus from '$fusString'")
    ReworkedTerminalUsageCollector.logFocusGained(fusString)
  }

  private fun logFocusLeavingTerminal(nextState: FocusedComponent) {
    val fusString = nextState.toFusString()
    if (fusString == null) {
      LOG.warn("Focus is leaving the terminal, but it's not known where it's going. This is a bug")
      return
    }
    LOG.debug("Focus is going from the terminal to '$fusString'")
    ReworkedTerminalUsageCollector.logFocusLost(fusString)
  }
}

private sealed class FocusedComponent {
  abstract fun toFusString(): String?
}

private data object InitialState : FocusedComponent() {
  override fun toFusString(): String? = null
}

private data object FocusedTerminal : FocusedComponent() {
  override fun toFusString(): String = TerminalToolWindowFactory.TOOL_WINDOW_ID
}

private data class FocusedToolWindow(val id: String) : FocusedComponent() {
  override fun toFusString(): String = id
}

private data class FocusedNonToolWindow(val focus: TerminalNonToolWindowFocus) : FocusedComponent() {
  override fun toFusString(): String = focus.name
}

private val FocusedComponent.isTerminalFocused: Boolean
  get() = this is FocusedTerminal

private val LOG = logger<TerminalFocusFusService>()
