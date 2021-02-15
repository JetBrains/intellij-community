// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector

class ShowMavenConnectorsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    MavenActionsUsagesCollector.trigger(e.project, MavenActionsUsagesCollector.ActionID.ShowMavenConnectors)
    MavenConnectorList().show()
  }

}
