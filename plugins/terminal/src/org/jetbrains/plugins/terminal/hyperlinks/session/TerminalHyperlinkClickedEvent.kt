// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import com.intellij.openapi.editor.event.EditorMouseEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId

@ApiStatus.Internal
@Serializable
data class TerminalHyperlinkClickedEvent(
  val hyperlinkId: TerminalHyperlinkId,
  @Transient val mouseEvent: EditorMouseEvent? = null,
)