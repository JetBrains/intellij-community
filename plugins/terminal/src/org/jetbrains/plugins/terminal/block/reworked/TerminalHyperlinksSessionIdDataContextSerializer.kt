// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId

internal class TerminalHyperlinksSessionIdDataContextSerializer : CustomDataContextSerializer<TerminalHyperlinksSessionId> {
  override val key: DataKey<TerminalHyperlinksSessionId>
    get() = TerminalHyperlinksSessionId.DATA_KEY
  override val serializer: KSerializer<TerminalHyperlinksSessionId>
    get() = TerminalHyperlinksSessionId.serializer()
}
