@file:OptIn(FlowPreview::class)

package com.intellij.terminal.frontend.fus

import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalNonToolWindowFocus
import java.awt.AWTEvent.FOCUS_EVENT_MASK
import java.awt.AWTEvent.WINDOW_FOCUS_EVENT_MASK
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.FocusEvent
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.APP)
internal class TerminalFocusFusService(private val coroutineScope: CoroutineScope) {

  private val initialized = AtomicBoolean(false)

  private val stateFlow = MutableStateFlow<FocusedComponent>(InitialState)

  private fun updateState(hasFocusedWindow: Boolean, focusedComponent: Component?) {
    stateFlow.value = computeCurrentState(hasFocusedWindow, focusedComponent)
  }

  private fun computeCurrentState(hasFocusedWindow: Boolean, focusedComponent: Component?): FocusedComponent {
    return when {
      focusedComponent == null || !hasFocusedWindow -> {
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

  private fun ensureInitialized() {
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
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    updateState(focusManager.focusedWindow != null, focusManager.focusOwner)
  }

  private fun installAWTListener() {
    val listener = AWTEventListener { event ->
      val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
      reduceFocusEvent(event.id, event.source as? Component, focusManager.focusedWindow != null, focusManager.focusOwner)
        ?.let { updateState(it.hasFocusedWindow, it.focusedComponent) }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, FOCUS_EVENT_MASK or WINDOW_FOCUS_EVENT_MASK)
    // Remove the listener once the service scope completes. No Component/Window references are
    // retained anywhere (only the derived focus category is stored), so no extra nulling is needed.
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }
  }

  internal fun updateFocusStateForTest(hasFocusedWindow: Boolean, focusedComponent: Component?): String? {
    updateState(hasFocusedWindow, focusedComponent)
    return stateFlow.value.toFusString()
  }

  private suspend fun collectState() {
    var previousState: FocusedComponent = InitialState
    stateFlow.debounce(500.milliseconds).distinctUntilChanged().collectLatest { state ->
      val action = focusLogAction(
        previousState.isTerminalFocused, previousState.toFusString(),
        state.isTerminalFocused, state.toFusString(),
      )
      when (action) {
        is FocusLogAction.EnterTerminal -> logFocusEnteringTerminal(action.from)
        is FocusLogAction.LeaveTerminal -> logFocusLeavingTerminal(action.to)
        FocusLogAction.None -> {}
      }
      previousState = state
    }
  }

  private fun logFocusEnteringTerminal(previousFus: String?) {
    if (previousFus == null) {
      LOG.debug("Not logging the terminal focus event because it's the first focused component")
      return
    }
    LOG.debug("The terminal gained focus from '$previousFus'")
    ReworkedTerminalUsageCollector.logFocusGained(previousFus)
  }

  private fun logFocusLeavingTerminal(nextFus: String?) {
    if (nextFus == null) {
      LOG.warn("Focus is leaving the terminal, but it's not known where it's going. This is a bug")
      return
    }
    LOG.debug("Focus is going from the terminal to '$nextFus'")
    ReworkedTerminalUsageCollector.logFocusLost(nextFus)
  }

  companion object {
    fun ensureInitialized() {
      service<TerminalFocusFusService>().ensureInitialized()
    }
  }
}

@TestOnly
fun createTerminalFocusFusServiceForTest(coroutineScope: CoroutineScope): Any {
  return TerminalFocusFusService(coroutineScope)
}

@TestOnly
fun updateTerminalFocusFusStateForTest(service: Any, hasFocusedWindow: Boolean, focusedComponent: Component?): String? {
  return (service as TerminalFocusFusService).updateFocusStateForTest(hasFocusedWindow, focusedComponent)
}

/** Returns the (hasFocusedWindow, focusedComponent) inputs the given AWT focus event maps to, or `null` for an unrelated event. */
@TestOnly
fun reduceTerminalFocusEventForTest(
  eventId: Int,
  eventSource: Component?,
  focusedWindowPresent: Boolean,
  focusOwner: Component?,
): Pair<Boolean, Component?>? =
  reduceFocusEvent(eventId, eventSource, focusedWindowPresent, focusOwner)?.let { it.hasFocusedWindow to it.focusedComponent }

/** Returns the focus-transition logging decision as (action, fusString), where action is "ENTER", "LEAVE", or "NONE". */
@TestOnly
fun terminalFocusLogActionForTest(
  previousTerminalFocused: Boolean,
  previousFus: String?,
  nextTerminalFocused: Boolean,
  nextFus: String?,
): Pair<String, String?> =
  when (val action = focusLogAction(previousTerminalFocused, previousFus, nextTerminalFocused, nextFus)) {
    is FocusLogAction.EnterTerminal -> "ENTER" to action.from
    is FocusLogAction.LeaveTerminal -> "LEAVE" to action.to
    FocusLogAction.None -> "NONE" to null
  }

private sealed class FocusedComponent {
  abstract fun toFusString(): String?
}

private data class FocusInputs(val hasFocusedWindow: Boolean, val focusedComponent: Component?)

/**
 * Maps a raw AWT focus event to the focus inputs the state is derived from, without retaining the event.
 * `focusedWindowPresent`/`focusOwner` come from the [KeyboardFocusManager] at the time of the event.
 */
private fun reduceFocusEvent(
  eventId: Int,
  eventSource: Component?,
  focusedWindowPresent: Boolean,
  focusOwner: Component?,
): FocusInputs? = when (eventId) {
  WindowEvent.WINDOW_GAINED_FOCUS -> FocusInputs(hasFocusedWindow = true, focusedComponent = focusOwner)
  WindowEvent.WINDOW_LOST_FOCUS -> FocusInputs(hasFocusedWindow = false, focusedComponent = null)
  FocusEvent.FOCUS_GAINED -> FocusInputs(hasFocusedWindow = focusedWindowPresent, focusedComponent = eventSource)
  else -> null
}

private sealed interface FocusLogAction {
  data object None : FocusLogAction

  /** Focus entered the terminal. [from] is the previous FUS string, or `null` for the first focused component (skip logging). */
  data class EnterTerminal(val from: String?) : FocusLogAction

  /** Focus left the terminal. [to] is the next FUS string, or `null` if the destination is unknown (a bug). */
  data class LeaveTerminal(val to: String?) : FocusLogAction
}

/** Decides what to log on a focus transition. Depends only on whether each state is the terminal and its FUS string. */
private fun focusLogAction(
  previousTerminalFocused: Boolean,
  previousFus: String?,
  nextTerminalFocused: Boolean,
  nextFus: String?,
): FocusLogAction = when {
  !previousTerminalFocused && nextTerminalFocused -> FocusLogAction.EnterTerminal(previousFus)
  previousTerminalFocused && !nextTerminalFocused -> FocusLogAction.LeaveTerminal(nextFus)
  else -> FocusLogAction.None
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
