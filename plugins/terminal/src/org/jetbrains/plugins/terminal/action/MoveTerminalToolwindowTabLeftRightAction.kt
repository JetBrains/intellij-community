// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener

open class MoveTerminalToolwindowTabLeftRightAction(private val moveLeft: Boolean) : TerminalSessionContextMenuActionBase(), DumbAware {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    move(content, e.project)
  }

  override fun update(e: AnActionEvent, activeToolWindow: ToolWindow, selectedContent: Content?) {
    super.update(e, activeToolWindow, selectedContent)
    if (e.presentation.isEnabledAndVisible) {
      e.presentation.isEnabled = isAvailable(selectedContent) && e.project != null
    }
  }

  fun isAvailable(content: Content?) : Boolean {
    val manager = content?.manager ?: return false
    val ind = manager.getIndexOfContent(content)
    return if (moveLeft) ind > 0 else ind >= 0 && ind < manager.contentCount - 1
  }

  fun move(content: Content?, project: Project?) {
    val manager = content?.manager ?: return
    val ind = manager.getIndexOfContent(content)
    val otherInd = if (moveLeft) ind - 1 else ind + 1
    if (ind >= 0 && otherInd >= 0 && otherInd < manager.contentCount && project != null) {
      val otherContent = manager.getContent(otherInd)!!
      TerminalTabCloseListener.executeContentOperationSilently(otherContent) {
        manager.removeContent(otherContent, false, false, false).doWhenDone {
          manager.addContent(otherContent, ind)
          TerminalTabCloseListener(otherContent, project)
        }
      }
    }
  }
}

class MoveTerminalToolWindowTabLeftAction : MoveTerminalToolwindowTabLeftRightAction(true)

class MoveTerminalToolWindowTabRightAction : MoveTerminalToolwindowTabLeftRightAction(false)
