// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

internal class TerminalSessionIdDataContextSerializer : CustomDataContextSerializer<TerminalSessionId> {
  override val key: DataKey<TerminalSessionId>
    get() = TerminalSessionId.KEY
  override val serializer: KSerializer<TerminalSessionId>
    get() = TerminalSessionId.serializer()
}
