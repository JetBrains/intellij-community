// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.rpc

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
sealed interface TerminalHyperlinksInputEvent {
  @Serializable
  data class ContentUpdated(val update: TerminalOutputUpdateDto) : TerminalHyperlinksInputEvent
}