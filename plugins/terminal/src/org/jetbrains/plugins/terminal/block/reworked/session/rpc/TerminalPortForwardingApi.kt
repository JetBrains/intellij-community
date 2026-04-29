// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Implementation-detail RPC for the Terminal Port Forwarding in split-mode (thin-client → backend).
 * Expected to be used only in the split-mode. Isn't required in the monolith mode.
 *
 * ### Terminology
 *
 * In this API:
 * - **Remote port** is a port on the **backend host** — the machine that runs the IDE backend
 *   (and where the user's process started from the terminal, e.g. `python3 -m http.server 8080`, is actually listening).
 * - **Local port** is a port on the **thin-client host** — the user's local machine running the
 *   client UI, the side from which the user opens a browser at `http://localhost:<localPort>`.
 *
 * The backend establishes the actual TCP tunnel.
 * This RPC simply lets the thin client request forwarding and observe the resulting `(remotePort, localPort)` mappings.
 */
@ApiStatus.Internal
@Rpc
interface TerminalPortForwardingApi : RemoteApi<Unit> {
  /**
   * Stream of port-forwarding events for the calling client.
   *
   * On collect, the backend first emits one [TerminalPortForwardingEvent.PortAdded] per port that
   * is already forwarded for this client (snapshot), then forwards live `portAdded`/`portRemoved`
   * events until the subscriber cancels.
   */
  suspend fun events(): Flow<TerminalPortForwardingEvent>

  /**
   * Forwards [remotePort] (a port listening on the backend host) to a port on the thin-client host.
   * Returns the bound **local port** on the thin client, or `null` if the proxy could not be established.
   */
  suspend fun forwardPort(remotePort: Int): Int?

  /**
   * Tears down the forwarding for [remotePort] on the backend host. Idempotent.
   */
  suspend fun stopForwarding(remotePort: Int)

  /**
   * Persists [remotePort] for [projectId] so that the backend automatically restores the forwarding
   * the next time the project is opened. No-op if [remotePort] is not currently forwarded.
   */
  suspend fun persistPort(projectId: ProjectId, remotePort: Int)

  /**
   * Removes [remotePort] from persistence for [projectId]. Idempotent.
   */
  suspend fun deletePersistedPort(projectId: ProjectId, remotePort: Int)

  companion object {
    suspend fun getInstance(): TerminalPortForwardingApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalPortForwardingApi>())
    }
  }
}

/**
 * Event describing a change in the set of ports forwarded for one client.
 *
 * - [PortAdded.remotePort] / [PortRemoved.remotePort] is the port on the **backend host**.
 * - [PortAdded.localPort] is the port on the **thin-client host** — what the user connects to from a browser.
 */
@ApiStatus.Internal
@Serializable
sealed interface TerminalPortForwardingEvent {
  @Serializable
  data class PortAdded(val remotePort: Int, val localPort: Int) : TerminalPortForwardingEvent

  @Serializable
  data class PortRemoved(val remotePort: Int) : TerminalPortForwardingEvent
}