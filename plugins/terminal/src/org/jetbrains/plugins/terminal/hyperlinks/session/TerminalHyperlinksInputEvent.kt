// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import com.intellij.platform.eel.annotations.NativePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
sealed interface TerminalHyperlinksInputEvent {
  @Serializable
  data class ContentUpdated(val update: TerminalOutputContentUpdateDto) : TerminalHyperlinksInputEvent

  @Serializable
  data class WorkingDirectoryChanged(val workingDirectory: @NativePath String) : TerminalHyperlinksInputEvent
}