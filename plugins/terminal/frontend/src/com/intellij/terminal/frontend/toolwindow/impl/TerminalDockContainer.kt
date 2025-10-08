package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.editor.TerminalViewVirtualFile
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.content.ContentManager
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.DockableContent
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl
import java.awt.Component
import javax.swing.JComponent

/**
 * Terminal tab can be moved to the editor using [org.jetbrains.plugins.terminal.action.MoveTerminalSessionToEditorAction].
 * This dock container is responsible for an ability to drop the editor tab with the terminal
 * back to the terminal tool window.
 */
internal class TerminalDockContainer private constructor(
  private val project: Project,
  private val component: JComponent,
) : DockContainer {
  override fun getAcceptArea(): RelativeRectangle {
    return RelativeRectangle(component)
  }

  override fun getContentResponse(content: DockableContent<*>, point: RelativePoint?): DockContainer.ContentResponse {
    return if (content.getClassicTerminalFile() != null || content.getReworkedTerminalFile() != null) {
      DockContainer.ContentResponse.ACCEPT_MOVE
    }
    else DockContainer.ContentResponse.DENY
  }

  override fun getContainerComponent(): JComponent {
    return component
  }

  override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
    if (dropTarget == null) return

    // Find the right split to create the new tab in
    val component = dropTarget.originalComponent
    val point = dropTarget.originalPoint
    val deepestComponent = UIUtil.getDeepestComponentAt(component, point.x, point.y)
    val nearestManager = findNearestContentManager(deepestComponent)

    val classicTerminalFile = content.getClassicTerminalFile()
    val reworkedTerminalFile = content.getReworkedTerminalFile()
    if (reworkedTerminalFile != null) {
      val manager = TerminalToolWindowTabsManager.getInstance(project)
      manager.attachTab(reworkedTerminalFile.terminalView, nearestManager)
    }
    else if (classicTerminalFile != null) {
      val engine = TerminalEngine.CLASSIC // Engine doesn't matter here because we will reuse the existing terminal widget.
      val manager = TerminalToolWindowManager.getInstance(project)
      val newContent = manager.createNewTab(nearestManager, classicTerminalFile.terminalWidget, manager.terminalRunner,
                                            engine, null, true, true)
      newContent.setDisplayName(classicTerminalFile.name)
    }
  }

  private fun findNearestContentManager(component: Component?): ContentManager? {
    if (component == null) return null
    val dataContext = DataManager.getInstance().getDataContext(component)
    return dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER)
  }

  override fun isEmpty(): Boolean {
    return false
  }

  override fun isDisposeWhenEmpty(): Boolean {
    return false
  }

  private fun DockableContent<*>.getClassicTerminalFile(): TerminalSessionVirtualFileImpl? {
    return getKey() as? TerminalSessionVirtualFileImpl
  }

  private fun DockableContent<*>.getReworkedTerminalFile(): TerminalViewVirtualFile? {
    return getKey() as? TerminalViewVirtualFile
  }

  companion object {
    @JvmStatic
    fun install(project: Project, component: JComponent) {
      component.launchOnShow("TerminalDockContainer") {
        val container = TerminalDockContainer(project, component)
        val disposable = Disposer.newDisposable("TerminalDockContainer")
        DockManager.getInstance(project).register(container, disposable)

        try {
          awaitCancellation()
        }
        finally {
          // Dispose on EDT as well because registering/unregistering the dock container is not thread-safe.
          Disposer.dispose(disposable)
        }
      }
    }
  }
}