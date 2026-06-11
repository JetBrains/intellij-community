package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil.getFileListFromAttachedObject
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.psi.PsiFileSystemItem
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.asDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import java.nio.file.Path

/**
 * Handles terminal drag-and-drop behavior
 *
 * - Drop on the terminal view: inserts dropped file/directory paths into the active terminal
 * - Drop on the tab bar: creates a new terminal tab using the first item's directory as the working directory
 *
 * Supports drops from Project View (PSI elements) and native OS file managers
 */
internal object TerminalDnDHandler {
  fun installHandler(window: ToolWindowEx, coroutineScope: CoroutineScope) {
    val handler = getDropHandler(window, coroutineScope)

    DnDSupport.createBuilder(window.decorator)
      .setDropHandler(handler)
      .setDisposableParent(coroutineScope.asDisposable())
      .enableAsNativeTarget()
      .disableAsSource()
      .install()
  }

  private fun getDropHandler(window: ToolWindowEx, coroutineScope: CoroutineScope): DnDDropHandler = DnDDropHandler { event ->
    val dataContext = getDataContext(event) ?: return@DnDDropHandler
    val terminalView = dataContext.getData(TerminalView.DATA_KEY)
    if (terminalView != null) {
      handleDropOnTerminalView(event, terminalView)
    }
    else {
      handleDropOnTab(window, coroutineScope, event, dataContext)
    }
  }

  private fun handleDropOnTerminalView(event: DnDEvent, terminalView: TerminalView) {
    val data = TerminalDropData(event)
    val context = getTerminalContext(terminalView) ?: return

    terminalView.coroutineScope.launch {
      val droppedFiles = data.virtualFiles ?: TerminalFilePathHandler.resolveVirtualFiles(data.paths)
      val textToInsert = TerminalFilePathHandler.getFilesAsText(droppedFiles, context).ifEmpty { return@launch }

      terminalView.createSendTextBuilder()
        .useBracketedPasteMode()
        .send(textToInsert)
    }
  }

  private fun handleDropOnTab(window: ToolWindowEx, coroutineScope: CoroutineScope, event: DnDEvent, dataContext: DataContext) {
    val contentManager = dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER) ?: return
    val data = TerminalDropData(event)

    coroutineScope.launch {
      val project = window.project
      val droppedFiles = data.virtualFiles ?: TerminalFilePathHandler.resolveVirtualFiles(data.paths)
      val dir = getDirectory(droppedFiles.firstOrNull(), project) ?: return@launch

      val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.DND_FILE_TO_TOOLWINDOW)
      withContext(Dispatchers.EDT) {
        createTerminalTab(
          project,
          workingDirectory = dir.path,
          contentManager = contentManager,
          startupFusInfo = fusInfo
        )
      }
    }
  }

  private fun getDataContext(event: DnDEvent): DataContext? {
    val handlerComponent = event.handlerComponent
    val point = event.point
    if (handlerComponent == null || point == null) return null

    val deepestComponent = UIUtil.getDeepestComponentAt(handlerComponent, point.x, point.y) ?: return null
    return DataManager.getInstance().getDataContext(deepestComponent)
  }

  private fun getDirectory(file: VirtualFile?, project: Project): VirtualFile? {
    if (file == null) return null

    val filePath = file.toNioPathOrNull() ?: return null
    if (!TerminalFilePathHandler.isSameEnvironment(filePath, project.getEelDescriptor()))
      return null

    return if (file.isDirectory) file else file.parent
  }
}

internal class TerminalDropData(event: DnDEvent) {
  val virtualFiles: List<VirtualFile>? = (event.attachedObject as? TransferableWrapper)
    ?.getPsiElements()
    ?.filterIsInstance<PsiFileSystemItem>()
    ?.map { it.virtualFile }
    ?.takeIf { it.isNotEmpty() }

  val paths: List<Path> = if (virtualFiles == null)
    getFileListFromAttachedObject(event.attachedObject).map { it.toPath() }
  else emptyList()
}