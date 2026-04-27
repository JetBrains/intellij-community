// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.eel.EelDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Per terminal‑tab view of the ports detected on the tab's environment and their current
 * forwarding state. The actual TCP forwarding is performed by [TerminalPortForwardingManager].
 * This model is what the UI observes and what actions read from the data context.
 *
 * All operations are thread-safe.
 */
internal class PortForwardingViewModel(val eelDescriptor: EelDescriptor) {
  private val mutableItems = MutableStateFlow<List<PortForwardingItem>>(emptyList())
  val items: StateFlow<List<PortForwardingItem>> = mutableItems

  /** Records that [remotePort] is currently forwarded to [localPort]. */
  fun setForwarded(remotePort: Int, localPort: Int) {
    replaceOrAppend(PortForwardingItem.Forwarded(remotePort, localPort))
  }

  /** Records that [remotePort] is detected but not (or no longer) forwarded. */
  fun setNotForwarded(remotePort: Int) {
    replaceOrAppend(PortForwardingItem.NotForwarded(remotePort))
  }

  /** Drops [remotePort] from the model. */
  fun removePort(remotePort: Int) {
    mutableItems.update { current -> current.filter { it.remotePort != remotePort } }
  }

  private fun replaceOrAppend(newItem: PortForwardingItem) {
    mutableItems.update { current ->
      val index = current.indexOfFirst { it.remotePort == newItem.remotePort }
      if (index >= 0) {
        current.toMutableList().also { it[index] = newItem }
      }
      else current + newItem
    }
  }

  companion object {
    val KEY: DataKey<PortForwardingViewModel> = DataKey.create("PortForwardingViewModel")
  }
}

internal sealed interface PortForwardingItem {
  val remotePort: Int

  data class Forwarded(override val remotePort: Int, val localPort: Int) : PortForwardingItem

  data class NotForwarded(override val remotePort: Int) : PortForwardingItem

  companion object {
    val KEY: DataKey<PortForwardingItem> = DataKey.create("PortForwardingItem")
  }
}