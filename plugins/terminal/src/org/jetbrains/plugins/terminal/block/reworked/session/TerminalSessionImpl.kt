// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalOutputEvent

internal class TerminalSessionImpl(
  override val inputChannel: SendChannel<TerminalInputEvent>,
  override val outputChannel: ReceiveChannel<List<TerminalOutputEvent>>,
) : TerminalSession