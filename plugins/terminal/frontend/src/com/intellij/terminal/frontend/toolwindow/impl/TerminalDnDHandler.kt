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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.psi.PsiFileSystemItem
import com.intellij.terminal.frontend.toolwindow.impl.TerminalFilePathHandler.getPathAsText
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
import kotlin.io.path.isDirectory

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
      val text = data.virtualFiles?.let { handleVirtualFiles(it, context) } ?: getPathAsText(data.paths, context)

      terminalView.createSendTextBuilder()
        .useBracketedPasteMode()
        .send(text)
    }
  }

  private fun handleDropOnTab(window: ToolWindowEx, coroutineScope: CoroutineScope, event: DnDEvent, dataContext: DataContext) {
    val contentManager = dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER) ?: return
    val data = TerminalDropData(event)

    coroutineScope.launch {
      val droppedFiles = data.virtualFiles?.mapNotNull { it.toNioPathOrNull() } ?: data.paths
      val filePath = droppedFiles.firstOrNull()
      if (!TerminalFilePathHandler.isSameEnvironment(filePath, window.project.getEelDescriptor()))
        return@launch

      val dir = getDirectory(filePath) ?: return@launch
      val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.DND_FILE_TO_TOOLWINDOW)
      withContext(Dispatchers.EDT) {
        createTerminalTab(
          window.project,
          workingDirectory = dir.toString(),
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

  private fun handleVirtualFiles(files: List<VirtualFile>, terminalContext: TerminalProcessContext): String {
    // In RemDev frontend, only paths from the remote machine should be inserted.
    // This proxy is intentionally conservative: it is not a perfect remote-file check,
    // but it accepts files dropped from the remote Project View.
    val isRemDev = IdeProductMode.isFrontend

    val paths = files.mapNotNull {
      val nioPath = it.toNioPathOrNull()
      if (nioPath != null)
        TerminalFilePathHandler.formatPath(nioPath, terminalContext)
      else
        if (isRemDev) it.path else null
    }
    return paths.joinToString(separator = " ")
  }

  private fun getDirectory(filePath: Path?): Path? {
    if (filePath == null) return null
    return if (filePath.isDirectory()) filePath else filePath.parent
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