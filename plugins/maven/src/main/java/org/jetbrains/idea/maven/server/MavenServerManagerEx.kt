// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.execution.SyncBundle
import java.util.function.Consumer

internal class MavenServerManagerEx {
  companion object {
    @JvmStatic
    fun stopConnectors(project: Project, wait: Boolean, connectors: List<MavenServerConnector>) {
      val cs = CoroutineScope(SupervisorJob())
      cs.launch {
        val taskCancellation = TaskCancellation.nonCancellable()
        withBackgroundProgress(project, SyncBundle.message("maven.sync.restarting"), taskCancellation) {
          connectors.forEach(Consumer { it: MavenServerConnector -> it.stop(wait) })
        }
      }
    }
  }
}