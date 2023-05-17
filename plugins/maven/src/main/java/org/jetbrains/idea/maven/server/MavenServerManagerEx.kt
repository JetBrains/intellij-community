// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.SyncBundle
import java.util.function.Consumer

internal class MavenServerManagerEx {
  companion object {
    @JvmStatic
    fun stopConnectors(project: Project?, wait: Boolean, connectors: List<MavenServerConnector>) {
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, SyncBundle.message("maven.sync.restarting"), false) {
        override fun run(indicator: ProgressIndicator) {
          connectors.forEach(Consumer { it: MavenServerConnector -> it.stop(wait) })
        }
      })
    }
  }
}