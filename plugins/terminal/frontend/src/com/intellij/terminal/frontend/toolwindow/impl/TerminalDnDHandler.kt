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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.psi.PsiFileSystemItem
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.completion.escapeShellArgument
import com.intellij.util.asDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.startup.TerminalLocalPathTranslator
import org.jetbrains.plugins.terminal.util.getNow
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
    val context = getTerminalDropContext(terminalView) ?: return
    val data = TerminalDropData(event)

    terminalView.coroutineScope.launch {
      val droppedFiles = data.virtualFiles ?: resolveVirtualFiles(data.paths)
      val textToInsert = getTextToInsertForFiles(droppedFiles, context).ifEmpty { return@launch }

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
      val droppedFiles = data.virtualFiles ?: resolveVirtualFiles(data.paths)
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
    if (!isSameEnvironment(filePath, project.getEelDescriptor()))
      return null

    return if (file.isDirectory) file else file.parent
  }

  private fun getTerminalDropContext(terminalView: TerminalView): TerminalDropContext? {
    val eelDescriptor = terminalView.sessionDeferred.getNow()?.eelDescriptor ?: return null
    val shellName = terminalView.startupOptionsDeferred.getNow()?.guessShellName() ?: ShellName.of("unknown")
    return TerminalDropContext(eelDescriptor, shellName)
  }

  private fun getTextToInsertForFiles(files: List<VirtualFile>, context: TerminalDropContext): String {
    return files.mapNotNull { file -> getPathToInsert(file, context.eelDescriptor) }
      .joinToString(" ") { path -> escapeShellArgument(path, context.shellName) }
  }

  private fun getPathToInsert(file: VirtualFile, eelDescriptor: EelDescriptor): String? {
    // RemDev and Monolith modes expose different VFS shapes, so keep the checks separate.
    return if (IdeProductMode.isFrontend) getPathInFrontend(file) else getPathInMonolith(file, eelDescriptor)
  }

  private fun getPathInFrontend(file: VirtualFile): String? {
    // In RemDev frontend, only paths from the remote machine should be inserted.
    // This proxy is intentionally conservative: it is not a perfect remote-file check,
    // but it accepts files dropped from the remote Project View.
    return file.path.takeIf { !file.isInLocalFileSystem }
  }

  private fun getPathInMonolith(file: VirtualFile, eelDescriptor: EelDescriptor): String? {
    // In monolith, VFS files should be local first.
    if (!file.isInLocalFileSystem) return null

    val nioPath = file.toNioPathOrNull() ?: return null
    // Normal case: paste only paths from the same EEL machine as the shell. This covers local,
    // WSL, and Docker paths that already belong to the shell environment.
    if (isSameEnvironment(nioPath, eelDescriptor)) {
      return runCatching { nioPath.asEelPath().toString() }.getOrNull()
    }

    // Special case for WSL shells: Windows drives are mounted inside WSL, so a local Windows file
    // can still be meaningful to the shell after translation, for example C:\work -> /mnt/c/work.
    return translateLocalPathToWsl(nioPath, eelDescriptor)
  }

  private fun isSameEnvironment(filePath: Path, eelDescriptor: EelDescriptor): Boolean {
    val fileMachine = filePath.getEelDescriptor().getResolvedEelMachine() ?: return false
    val eelMachine = eelDescriptor.getResolvedEelMachine() ?: return false
    return fileMachine == eelMachine
  }

  private fun translateLocalPathToWsl(nioPath: Path, eelDescriptor: EelDescriptor): String? {
    val fileDescriptor = nioPath.getEelDescriptor()
    if (fileDescriptor != LocalEelDescriptor || !LocalEelDescriptor.osFamily.isWindows || !eelDescriptor.osFamily.isPosix) {
      return null
    }

    return TerminalLocalPathTranslator(eelDescriptor).translateAbsoluteLocalPathToRemote(nioPath)?.toString()
  }

  private suspend fun resolveVirtualFiles(paths: List<Path>): List<VirtualFile> = withContext(Dispatchers.IO) {
    paths.mapNotNull { path -> VfsUtil.findFile(path, true) }
  }

  private data class TerminalDropContext(
    val eelDescriptor: EelDescriptor,
    val shellName: ShellName,
  )
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