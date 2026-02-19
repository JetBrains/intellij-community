package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.UIUtil

/**
 * Creates the new terminal tab on dropping the directory node inside the terminal tool window.
 * For example, from Project View.
 */
internal fun installDirectoryDnD(window: ToolWindowEx, parentDisposable: Disposable) {
  val handler = DnDDropHandler { event ->
    val tw = event.getAttachedObject() as? TransferableWrapper ?: return@DnDDropHandler
    val dir = getDirectory(tw.getPsiElements()?.singleOrNull()) ?: return@DnDDropHandler
    // Find the right split to create the new tab in
    val nearestManager = findNearestContentManager(event)
    createTerminalTab(
      window.project,
      workingDirectory = dir.getVirtualFile().getPath(),
      contentManager = nearestManager,
    )
  }
  DnDSupport.createBuilder(window.decorator)
    .setDropHandler(handler)
    .setDisposableParent(parentDisposable)
    .disableAsSource()
    .install()
}

private fun getDirectory(item: PsiElement?): PsiDirectory? {
  if (item is PsiFile) {
    return item.getParent()
  }
  return item as? PsiDirectory
}

private fun findNearestContentManager(event: DnDEvent): ContentManager? {
  val handlerComponent = event.handlerComponent
  val point = event.point
  if (handlerComponent == null || point == null) return null

  val deepestComponent = UIUtil.getDeepestComponentAt(handlerComponent, point.x, point.y) ?: return null
  val dataContext = DataManager.getInstance().getDataContext(deepestComponent)
  return dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER)
}