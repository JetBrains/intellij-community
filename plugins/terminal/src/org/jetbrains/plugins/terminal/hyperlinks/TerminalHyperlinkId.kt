// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.impl.EditorTextDecorationId
import com.intellij.execution.impl.createTextDecorationId
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TerminalHyperlinkId(val value: Long) {
  override fun toString(): String = value.toString()

  companion object {
    @JvmStatic
    val KEY: DataKey<TerminalHyperlinkId> = DataKey.create("TerminalHyperlinkId")
  }
}

@ApiStatus.Internal
fun TerminalHyperlinkId.toPlatformId(): EditorTextDecorationId = createTextDecorationId(value)

@ApiStatus.Internal
fun EditorTextDecorationId.toTerminalId(): TerminalHyperlinkId = TerminalHyperlinkId(value)
