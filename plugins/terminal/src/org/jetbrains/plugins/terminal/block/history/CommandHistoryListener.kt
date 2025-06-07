// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.history

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel

@ApiStatus.Internal
interface CommandHistoryListener {
  fun commandHistoryShown(promptModel: TerminalPromptModel) {}

  fun commandHistoryAborted(promptModel: TerminalPromptModel) {}

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<CommandHistoryListener> = Topic(CommandHistoryListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
