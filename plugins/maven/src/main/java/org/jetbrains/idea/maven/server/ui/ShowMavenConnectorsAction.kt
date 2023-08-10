// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector

class ShowMavenConnectorsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    MavenActionsUsagesCollector.trigger(e.project, MavenActionsUsagesCollector.SHOW_MAVEN_CONNECTORS)
    MavenConnectorList().show()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
