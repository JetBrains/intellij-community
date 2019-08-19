// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent

abstract class TerminalWatchManager {
   fun init(project: Project, terminalToolWindow: ToolWindow) {
    val contentManager = terminalToolWindow.contentManager
    for (content in contentManager.contents) {
      watchTab(project, content)
    }
    contentManager.addContentManagerListener(object : ContentManagerAdapter() {
      override fun contentAdded(event: ContentManagerEvent) {
        watchTab(project, event.content)
      }

      override fun contentRemoved(event: ContentManagerEvent) {
        unwatchTab(event.content)
      }
    })
  }

  protected abstract fun watchTab(project: Project, content: Content)

  protected abstract fun unwatchTab(content: Content)
}