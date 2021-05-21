// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalView

open class MoveTerminalToolwindowTabLeftRightAction(private val moveLeft: Boolean) : TerminalSessionContextMenuActionBase(), DumbAware {
  override fun actionPerformedInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content) {
    move(content, project)
  }

  override fun updateInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content) {
    e.presentation.isEnabled = isAvailable(content)
  }

  fun isAvailable(content: Content) : Boolean {
    val manager = content.manager ?: return false
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
          TerminalTabCloseListener(otherContent, project, TerminalView.getInstance(project))
        }
      }
    }
  }
}

class MoveTerminalToolWindowTabLeftAction : MoveTerminalToolwindowTabLeftRightAction(true)

class MoveTerminalToolWindowTabRightAction : MoveTerminalToolwindowTabLeftRightAction(false)
