// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidgetListener

class TerminalEditorWidgetListener(val project: Project, val file: TerminalSessionVirtualFileImpl): JBTerminalWidgetListener {
  override fun onNewSession() {
  }

  override fun onTerminalStarted() {
  }

  override fun onPreviousTabSelected() {
  }

  override fun onNextTabSelected() {
  }

  override fun onSessionClosed() {
    FileEditorManager.getInstance(project).closeFile(file)
  }

  override fun showTabs() {
  }
}