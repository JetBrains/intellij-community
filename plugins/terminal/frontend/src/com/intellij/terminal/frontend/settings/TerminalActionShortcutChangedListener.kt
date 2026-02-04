// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.settings

import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TERMINAL_ACTION_SHORTCUT_CHANGED_TOPIC
import org.jetbrains.plugins.terminal.TerminalActionShortcutChangedEvent
import org.jetbrains.plugins.terminal.util.updateActionShortcut

@ApiStatus.Internal
class TerminalActionShortcutChangedListener : ApplicationRemoteTopicListener<TerminalActionShortcutChangedEvent> {
  override val topic: ApplicationRemoteTopic<TerminalActionShortcutChangedEvent> = TERMINAL_ACTION_SHORTCUT_CHANGED_TOPIC

  override fun handleEvent(event: TerminalActionShortcutChangedEvent) {
    updateActionShortcut(event.actionId, event.shortcut)
  }
}
