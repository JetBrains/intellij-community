// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run.terminal

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Carries a "run this in the terminal" request from whoever asks ([com.intellij.sh.run.ShRunner], on any side of a
 * remote-development session) to the frontend that owns the Terminal tool window.
 *
 * In a monolith or a light IDE the request is delivered in-process; on a remote-development backend it goes to the client
 * that made the request. The listener lives in `intellij.sh.terminal.frontend`.
 */
@ApiStatus.Internal
object ShTerminalRunTopic {
  const val ID: String = "sh.run.in.terminal"

  val TOPIC: ProjectRemoteTopic<ShTerminalRunRequest> = ProjectRemoteTopic(ID, ShTerminalRunRequest.serializer())
}

/**
 * @param command          the shell line to execute, quoted for the target shell
 * @param workingDirectory the directory spelled as the target environment sees it (an `EelPath` string); a local nio path is
 *                         accepted as well
 * @param title            the tab title
 * @param activateToolWindow whether to bring the Terminal tool window to the front
 */
@ApiStatus.Internal
@Serializable
data class ShTerminalRunRequest(
  val command: String,
  val workingDirectory: String,
  val title: @NlsContexts.TabTitle String,
  val activateToolWindow: Boolean,
)
