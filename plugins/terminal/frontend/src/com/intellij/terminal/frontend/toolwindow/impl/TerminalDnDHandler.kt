package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

/**
 * Handles terminal drag-and-drop behavior
 *
 * - Drop on the terminal view: inserts dropped file/directory paths into the active terminal
 * - Drop on the tab bar: creates a new terminal tab using the first item's directory as the working directory
 */
internal object TerminalDnDHandler {
  fun installHandler(window: ToolWindowEx, parentDisposable: Disposable) {
    val handler = getDropHandler(window)

    DnDSupport.createBuilder(window.decorator)
      .setDropHandler(handler)
      .setDisposableParent(parentDisposable)
      .disableAsSource()
      .install()
  }

  private fun getDropHandler(window: ToolWindowEx): DnDDropHandler = DnDDropHandler { event ->
    val dataContext = getDataContext(event) ?: return@DnDDropHandler
    val terminalView = dataContext.getData(TerminalView.DATA_KEY)
    if (terminalView != null) {
      val files = getDroppedFiles(event).ifEmpty { return@DnDDropHandler }

      val textToInsert = files.joinToString(" ") { it.virtualFile.path }
      terminalView.sendText(textToInsert)
    }
    else {
      val contentManager = dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER) ?: return@DnDDropHandler
      val files = getDroppedFiles(event)
      val dir = getDirectory(files.firstOrNull()) ?: return@DnDDropHandler

      val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.DND_FILE_TO_TOOLWINDOW)
      createTerminalTab(
        window.project,
        workingDirectory = dir.virtualFile.path,
        contentManager = contentManager,
        startupFusInfo = fusInfo
      )
    }
  }

  private fun getDataContext(event: DnDEvent): DataContext? {
    val handlerComponent = event.handlerComponent
    val point = event.point
    if (handlerComponent == null || point == null) return null

    val deepestComponent = UIUtil.getDeepestComponentAt(handlerComponent, point.x, point.y) ?: return null
    return DataManager.getInstance().getDataContext(deepestComponent)
  }

  private fun getDroppedFiles(event: DnDEvent): List<PsiFileSystemItem> {
    val attached = event.getAttachedObject() as? TransferableWrapper ?: return emptyList()
    return attached.getPsiElements()?.filterIsInstance<PsiFileSystemItem>() ?: emptyList()
  }

  private fun getDirectory(item: PsiElement?): PsiDirectory? {
    if (item is PsiFile) {
      return item.getParent()
    }
    return item as? PsiDirectory
  }
}
