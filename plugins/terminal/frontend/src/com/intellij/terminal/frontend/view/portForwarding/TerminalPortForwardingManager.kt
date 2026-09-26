// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Application-level extension that performs TCP port forwarding from the IDE host environment to any
 * EEL environment and tracks the active forwardings so multiple terminals on the same physical
 * machine share a single tunnel per remote port.
 *
 * It is assumed that implementation is thread-safe, so all operations can be called from any thread.
 */
@ApiStatus.Internal
interface TerminalPortForwardingManager {
  /**
   * Emits whenever the set of currently forwarded ports changes for any environment
   * (a new forwarding is started or an existing one is stopped).
   *
   * Subscribers should re-query [getForwardedLocalPort] for the ports they care about.
   */
  val stateChangedFlow: Flow<Unit>

  /**
   * Returns the currently bound local port for [remotePort] in [eelMachine], or `null` if not forwarded.
   */
  fun getForwardedLocalPort(eelMachine: EelMachine, remotePort: Int): Int?

  /**
   * Forwards [remotePort] from [EelMachine] of [eelDescriptor] to a port on the IDE host.
   * If a forwarding already exists for this machine, returns its bound local port unchanged.
   * Otherwise, tries to bind the same number first ('8080 → 8080'); on failure asks the OS to pick.
   *
   * Returns `null` if the proxy could not be established.
   */
  suspend fun forwardPort(eelDescriptor: EelDescriptor, remotePort: Int): Int?

  /**
   * Tears down the forwarding for `(eelMachine, remotePort)` if one is registered.
   * Idempotent.
   */
  fun stopForwarding(eelMachine: EelMachine, remotePort: Int)

  companion object {
    private val EP_NAME: ExtensionPointName<TerminalPortForwardingManager> =
      ExtensionPointName.create("org.jetbrains.plugins.terminal.portForwardingManager")

    val DATA_KEY: DataKey<TerminalPortForwardingManager> = DataKey.create("TerminalPortForwardingManager")

    /**
     * Returns the highest-priority registered implementation: [TerminalPortForwardingManagerImpl] in the monolith,
     * or the Remote Dev thin-client implementation (declared with `order="first"`) when the RD client is loaded.
     */
    @JvmStatic
    fun getInstance(): TerminalPortForwardingManager {
      return EP_NAME.findFirstSafe { true }
             ?: error("No ${TerminalPortForwardingManager::class.simpleName} implementation is registered")
    }
  }
}