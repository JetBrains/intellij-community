// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/** Way for backend type module to signal that the project environment has changed (e.g. [JavaTerminalSettingsReloadNotifier])  */
@ApiStatus.Experimental
object TerminalEnvironmentChanged {
  val TOPIC: ProjectRemoteTopic<EnvironmentChange> = ProjectRemoteTopic("jb_terminal_request", EnvironmentChange.serializer())

  /**
   * @param environment What has changed (e.g. "JDK settings")
   */
  @Serializable
  data class EnvironmentChange(@Nls val environment: String)
}