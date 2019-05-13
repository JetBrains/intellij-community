// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.TerminateRemoteProcessDialog
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.BaseContentCloseListener
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content

class TerminalTabCloseListener(val content: Content,
                               val project: Project) : BaseContentCloseListener(content, project) {
  override fun disposeContent(content: Content) {
  }

  override fun closeQuery(content: Content, projectClosing: Boolean): Boolean {
    if (projectClosing) {
      return true
    }
    val widget = TerminalView.getWidgetByContent(content)
    if (widget == null || !widget.isSessionRunning) {
      return true
    }
    val proxy = NopProcessHandler().apply { startNotify() }
    // don't show 'disconnect' button
    proxy.putUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY, true)
    val result = TerminateRemoteProcessDialog.show(project, "Terminal ${content.displayName}", proxy)
    return result != null
  }

  override fun canClose(project: Project): Boolean {
    return project === this.project && closeQuery(this.content, true)
  }
}
