// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.hyperlinks

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId

internal class HyperlinkIdDataContextSerializer : CustomDataContextSerializer<TerminalHyperlinkId> {
  override val key: DataKey<TerminalHyperlinkId>
    get() = TerminalHyperlinkId.Companion.KEY
  override val serializer: KSerializer<TerminalHyperlinkId>
    get() = TerminalHyperlinkId.Companion.serializer()
}