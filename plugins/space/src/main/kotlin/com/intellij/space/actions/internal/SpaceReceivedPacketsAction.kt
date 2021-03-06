// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import libraries.klogging.logger

internal class SpaceReceivedPacketsAction : DumbAwareAction() {
  companion object {
    private val LOG = logger<SpaceReceivedPacketsAction>()
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val workspace = SpaceWorkspaceComponent.getInstance().workspace.value
    if (workspace == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    presentation.isEnabledAndVisible = true
    if (workspace.client.logReceivedPackets) {
      presentation.text = SpaceBundle.message("action.internal.stop.log.packets.text")
    }
    else {
      presentation.text = SpaceBundle.message("action.internal.start.log.packets.text")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val client = SpaceWorkspaceComponent.getInstance().workspace.value?.client ?: return
    if (client.logReceivedPackets) {
      LOG.info { "Logging packets is stopped" }
      client.logReceivedPackets = false
    }
    else {
      LOG.info { "Logging packets is started" }
      client.logReceivedPackets = true
    }
  }
}