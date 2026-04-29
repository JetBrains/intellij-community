// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Persists which terminal-detected ports the user has forwarded for a given project, so the platform
 * can re-forward them automatically the next time the project is opened.
 *
 * At the moment has only Split Mode-only implementation. Because there is an existing way of storing ports.
 * The monolith implementation is a no-op.
 */
@ApiStatus.Internal
interface TerminalPortForwardingPersistenceService {
  /** Marks [remotePort] as one that should be re-forwarded automatically next time the project is opened. */
  suspend fun persistPort(remotePort: Int)

  /** Removes [remotePort] from the auto-forwarded set. */
  suspend fun deletePersistedPort(remotePort: Int)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalPortForwardingPersistenceService = project.service()
  }
}

/** Default no-op implementation. There is no place for persisting forwarded ports in the Monolith mode yet. */
internal class TerminalPortForwardingNoOpPersistenceService : TerminalPortForwardingPersistenceService {
  override suspend fun persistPort(remotePort: Int) = Unit
  override suspend fun deletePersistedPort(remotePort: Int) = Unit
}