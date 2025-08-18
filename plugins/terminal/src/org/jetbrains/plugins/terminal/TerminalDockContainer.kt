// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockableContent
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl
import javax.swing.JComponent

/**
 * Terminal tab can be moved to the editor using [org.jetbrains.plugins.terminal.action.MoveTerminalSessionToEditorAction].
 * This dock container is responsible for an ability to drop the editor tab with the terminal
 * back to the terminal tool window.
 */
internal class TerminalDockContainer(private val toolWindow: ToolWindowEx) : DockContainer {
  override fun getAcceptArea(): RelativeRectangle {
    return RelativeRectangle(toolWindow.getDecorator())
  }

  override fun getContentResponse(content: DockableContent<*>, point: RelativePoint?): DockContainer.ContentResponse {
    return if (isTerminalSessionContent(content)) DockContainer.ContentResponse.ACCEPT_MOVE else DockContainer.ContentResponse.DENY
  }

  override fun getContainerComponent(): JComponent {
    return toolWindow.getDecorator()
  }

  override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
    if (dropTarget == null) return
    if (isTerminalSessionContent(content)) {
      // Find the right split to create the new tab in
      val component = dropTarget.originalComponent
      val point = dropTarget.originalPoint
      val deepestComponent = UIUtil.getDeepestComponentAt(component, point.x, point.y)
      val nearestManager = TerminalToolWindowManager.findNearestContentManager(deepestComponent)

      val terminalFile = content.getKey() as TerminalSessionVirtualFileImpl
      val engine = TerminalEngine.CLASSIC // Engine doesn't matter here because we will reuse the existing terminal widget.
      val manager = TerminalToolWindowManager.getInstance(toolWindow.getProject())
      val newContent = manager.createNewTab(nearestManager, terminalFile.terminalWidget, manager.terminalRunner, engine,
                                            null, null, null, true, true)
      newContent.setDisplayName(terminalFile.name)
    }
  }

  override fun isEmpty(): Boolean {
    return false
  }

  override fun isDisposeWhenEmpty(): Boolean {
    return false
  }

  private fun isTerminalSessionContent(content: DockableContent<*>): Boolean {
    return content.getKey() is TerminalSessionVirtualFileImpl
  }
}