package com.intellij.terminal.backend

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.terminal.session.TerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

internal object TerminalSessionValueIdType : BackendValueIdType<TerminalSessionId, TerminalSession>(::TerminalSessionId)