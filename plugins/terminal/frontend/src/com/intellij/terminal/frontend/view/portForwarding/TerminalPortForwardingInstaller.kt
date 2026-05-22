// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.execution.portsWatcher.ListeningPort
import com.intellij.execution.portsWatcher.ListeningPortHandler
import com.intellij.execution.portsWatcher.PortListeningOptions
import com.intellij.execution.portsWatcher.ProcessPortsWatcher
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.portAccessibleLocally.EelPortAccessibleLocally
import com.intellij.platform.eel.provider.resolveEelMachine
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.cursorOffsetFlow
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import kotlin.time.Duration.Companion.milliseconds

/**
 * Installs a port-forwarding panel above the terminal output for [terminalView].
 *
 * Waits for the terminal session to be ready, then (if port forwarding is required):
 * 1. Starts a [ProcessPortsWatcher] against the shell's EEL environment
 * and PID.
 * 2. Once listening port is detected, checks if port forwarding is already set up using [TerminalPortForwardingManager].
 * 3. Updates the [PortForwardingViewModel], so the [PortForwardingWidget] can render available ports.
 *
 * Lifecycle of all related logic is bound to the [coroutineScope].
 * Once it is canceled, watcher stops, all established forwardings are stopped, and the top component is removed.
 */
internal fun installPortForwarding(terminalView: TerminalView, coroutineScope: CoroutineScope) {
  // Trigger eager init.
  // It is required for ThinClientTerminalPortForwardingManager to receive port forwarding data from the backend before any interaction.
  TerminalPortForwardingManager.getInstance()

  coroutineScope.launch {
    val session = terminalView.sessionDeferred.await()
    val startupOptions = terminalView.startupOptionsDeferred.await()
    if (startupOptions.processType != TerminalProcessType.SHELL) {
      // Install port forwarding only for shell processes
      return@launch
    }

    val eelDescriptor = session.eelDescriptor
    if (arePortsAccessibleLocally(session.eelDescriptor)) {
      // No port forwarding required
      return@launch
    }

    val eelMachine = try {
      eelDescriptor.resolveEelMachine()
    }
    catch (e: EelUnavailableException) {
      LOG.error("Failed to resolve EEL machine for $eelDescriptor", e)
      return@launch
    }

    val model = PortForwardingViewModel(eelDescriptor)
    installViewModelUpdating(eelMachine, model, coroutineScope)

    val watcher = installPortsWatcher(
      eelMachine = eelMachine,
      processId = session.processId,
      model = model,
      coroutineScope = coroutineScope
    )
    resetWatcherOnCursorLineChange(terminalView, watcher, coroutineScope)

    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      val panelScope = coroutineScope.childScope("TerminalPortForwardingPanel")
      val panel = PortForwardingWidget(model, panelScope)
      terminalView.setTopComponent(panel, panelScope.asDisposable())
    }
  }
}

/**
 * Returns true if application running on "localhost:<port>" inside [eelDescriptor] is accessible locally
 * using the same "localhost:<port>" address. I.e., port forwarding is not required.
 */
private suspend fun arePortsAccessibleLocally(eelDescriptor: EelDescriptor): Boolean {
  // TODO: Had to use internal implementation-detail API. Public API required for this scenario.
  //  The port is chosen randomly. This check relies on the fact that if single port is accessible - other ones are accessible as well.
  return eelDescriptor == LocalEelDescriptor
         || EelPortAccessibleLocally.isEelPortAccessibleLocally(8080.toUShort(), 8080.toUShort(), eelDescriptor)
}

/**
 * Keeps each [PortForwardingItem]'s forwarded/not-forwarded state in sync with the [TerminalPortForwardingManager]
 * when its state changes (e.g., via [TerminalPortForwardingManager.forwardPort] or [TerminalPortForwardingManager.stopForwarding]).
 */
private fun installViewModelUpdating(
  eelMachine: EelMachine,
  model: PortForwardingViewModel,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(CoroutineName("")) {
    val manager = TerminalPortForwardingManager.getInstance()
    manager.stateChangedFlow.collect {
      for (item in model.items.value) {
        val localPort = manager.getForwardedLocalPort(eelMachine, item.remotePort)
        if (localPort != null) {
          model.setForwarded(item.remotePort, localPort)
        }
        else {
          model.setNotForwarded(item.remotePort)
        }
      }
    }
  }
}

private fun installPortsWatcher(
  eelMachine: EelMachine,
  processId: Long,
  model: PortForwardingViewModel,
  coroutineScope: CoroutineScope,
): ProcessPortsWatcher {
  val handler = object : ListeningPortHandler {
    override fun onPortListeningStarted(port: ListeningPort) {
      // Add the entry first so that setForwarded/setNotForwarded can mutate it.
      model.addPort(port.port)

      // Then resolve the initial forwarded/not-forwarded state from the manager.
      // Subsequent state changes are handled by the logic in [installViewModelUpdating].
      val existingLocalPort = TerminalPortForwardingManager.getInstance().getForwardedLocalPort(eelMachine, port.port)
      if (existingLocalPort != null) {
        model.setForwarded(port.port, existingLocalPort)
      }
      else {
        model.setNotForwarded(port.port)
      }
    }

    override fun onPortListeningEnded(port: ListeningPort) {
      // Let's remove port from the model only and do not stop actual port-forwarding in the TerminalPortForwardingManager
      // So, when a user starts the application listening on the same port, they won't need to forward it again.
      model.removePort(port.port)
    }
  }

  return ProcessPortsWatcher.startWatching(
    eelDescriptor = model.eelDescriptor,
    pid = processId,
    handler = handler,
    coroutineScope = coroutineScope,
    options = PortListeningOptions.INCLUDE_CHILDREN,
  )
}

/**
 * [ProcessPortsWatcher] is based on polling approach, so we need to reset its delay heuristically.
 * This method resets the delay when the cursor is moved to a different line.
 */
@OptIn(FlowPreview::class)
private fun resetWatcherOnCursorLineChange(
  terminalView: TerminalView,
  watcher: ProcessPortsWatcher,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement() + CoroutineName("TerminalPortsWatcher reset job")) {
    val outputModel = terminalView.outputModels.regular
    var curCursorLine = outputModel.getLineByOffset(outputModel.cursorOffset)
    outputModel.cursorOffsetFlow
      .sample(300.milliseconds)
      .collect {
        val cursorOffset = outputModel.cursorOffset
        val newLine = outputModel.getLineByOffset(cursorOffset)
        if (newLine != curCursorLine) {
          curCursorLine = newLine
          watcher.resetDelay()
        }
      }
  }
}

private val LOG = fileLogger()