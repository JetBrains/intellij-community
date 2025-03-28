// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

@ApiStatus.Internal
@Serializable
data class TerminalSessionTab(
  val id: Int,
  val name: String?,
  val isUserDefinedName: Boolean,
  val shellCommand: List<String>?,
  val sessionId: TerminalSessionId?,
  val portForwardingId: TerminalPortForwardingId?,
)