// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine

/**
 * Application-level service that performs TCP port forwarding from the IDE host environment to any
 * EEL environment and tracks the active forwardings so multiple terminals on the same physical
 * machine share a single tunnel per remote port.
 *
 * It is assumed that implementation is thread-safe, so all operations can be called from any thread.
 */
internal interface TerminalPortForwardingManager {
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
    @JvmStatic
    fun getInstance(): TerminalPortForwardingManager = service()
  }
}