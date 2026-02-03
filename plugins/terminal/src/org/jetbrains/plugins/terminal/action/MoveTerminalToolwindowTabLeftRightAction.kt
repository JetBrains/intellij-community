// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

open class MoveTerminalToolwindowTabLeftRightAction(private val moveLeft: Boolean) : ToolWindowContextMenuActionBase(), DumbAware {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project != null && content != null) {
      move(content, project)
    }
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    e.presentation.isVisible = e.project != null
                               && TerminalToolWindowManager.isTerminalToolWindow(toolWindow)
                               && content != null
    e.presentation.isEnabled = e.presentation.isVisible && isAvailable(content)
  }

  fun isAvailable(content: Content?): Boolean {
    val manager = content?.manager ?: return false
    val ind = manager.getIndexOfContent(content)
    return if (moveLeft) ind > 0 else ind >= 0 && ind < manager.contentCount - 1
  }

  fun move(content: Content, project: Project) {
    val manager = content.manager ?: return
    val ind = manager.getIndexOfContent(content)
    val otherInd = if (moveLeft) ind - 1 else ind + 1
    if (ind >= 0 && otherInd >= 0 && otherInd < manager.contentCount) {
      val otherContent = manager.getContent(otherInd)!!
      TerminalTabCloseListener.executeContentOperationSilently(otherContent) {
        manager.removeContent(otherContent, false, false, false).doWhenDone {
          manager.addContent(otherContent, ind)
        }
      }
    }
  }
}

class MoveTerminalToolWindowTabLeftAction : MoveTerminalToolwindowTabLeftRightAction(true)

class MoveTerminalToolWindowTabRightAction : MoveTerminalToolwindowTabLeftRightAction(false)
