// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel

@ApiStatus.Internal
interface BlockTerminalInitializationListener {
  fun modelsInitialized(promptModel: TerminalPromptModel, outputModel: TerminalOutputModel)

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<BlockTerminalInitializationListener> = Topic(BlockTerminalInitializationListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
