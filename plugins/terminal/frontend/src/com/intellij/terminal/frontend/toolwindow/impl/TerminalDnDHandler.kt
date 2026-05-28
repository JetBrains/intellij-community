package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
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
import com.intellij.util.ui.UIUtil
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
  fun installHandler(window: ToolWindowEx, parentDisposable: Disposable) {
    val handler = getDropHandler(window)

    DnDSupport.createBuilder(window.decorator)
      .setDropHandler(handler)
      .setDisposableParent(parentDisposable)
      .enableAsNativeTarget()
      .disableAsSource()
      .install()
  }

  private fun getDropHandler(window: ToolWindowEx): DnDDropHandler = DnDDropHandler { event ->
    val dataContext = getDataContext(event) ?: return@DnDDropHandler
    val terminalView = dataContext.getData(TerminalView.DATA_KEY)
    if (terminalView != null) {
      handleDropOnTerminalView(event, terminalView)
    }
    else {
      handleDropOnTab(event, dataContext, window)
    }
  }

  private fun handleDropOnTerminalView(event: DnDEvent, terminalView: TerminalView) {
    val context = getTerminalDropContext(terminalView) ?: return
    val textToInsert = getTextToInsertForFiles(getDroppedFiles(event), context).ifEmpty { return }
    terminalView.createSendTextBuilder()
      .useBracketedPasteMode()
      .send(textToInsert)
  }

  private fun handleDropOnTab(event: DnDEvent, dataContext: DataContext, window: ToolWindowEx) {
    val contentManager = dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER) ?: return
    val files = getDroppedFiles(event)
    val dir = getDirectory(files.firstOrNull()) ?: return

    val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.DND_FILE_TO_TOOLWINDOW)
    createTerminalTab(
      window.project,
      workingDirectory = dir.path,
      contentManager = contentManager,
      startupFusInfo = fusInfo
    )
  }

  private fun getDataContext(event: DnDEvent): DataContext? {
    val handlerComponent = event.handlerComponent
    val point = event.point
    if (handlerComponent == null || point == null) return null

    val deepestComponent = UIUtil.getDeepestComponentAt(handlerComponent, point.x, point.y) ?: return null
    return DataManager.getInstance().getDataContext(deepestComponent)
  }

  private fun getDirectory(file: VirtualFile?): VirtualFile? {
    if (file == null) return null
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
    return if (IdeProductMode.isFrontend) getPathInFrontend(file) else getPathInMonolith(file, eelDescriptor)
  }

  private fun getPathInFrontend(file: VirtualFile): String? {
    return file.path.takeIf { !file.isInLocalFileSystem }
  }

  private fun getPathInMonolith(file: VirtualFile, eelDescriptor: EelDescriptor): String? {
    if (!file.isInLocalFileSystem) return null

    val nioPath = file.toNioPathOrNull() ?: return null
    val fileDescriptor = nioPath.getEelDescriptor()
    val fileMachine = fileDescriptor.getResolvedEelMachine() ?: return null
    val eelMachine = eelDescriptor.getResolvedEelMachine() ?: return null
    if (fileMachine == eelMachine) {
      return runCatching { nioPath.asEelPath(fileDescriptor).toString() }.getOrNull()
    }

    return translateLocalPathToWsl(nioPath, fileDescriptor, eelDescriptor)
  }

  private fun translateLocalPathToWsl(nioPath: Path, fileDescriptor: EelDescriptor, eelDescriptor: EelDescriptor): String? {
    if (fileDescriptor != LocalEelDescriptor || !LocalEelDescriptor.osFamily.isWindows || !eelDescriptor.osFamily.isPosix) {
      return null
    }

    return TerminalLocalPathTranslator(eelDescriptor).translateAbsoluteLocalPathToRemote(nioPath)?.toString()
  }

  private data class TerminalDropContext(
    val eelDescriptor: EelDescriptor,
    val shellName: ShellName,
  )
}

internal fun getDroppedFiles(event: DnDEvent): List<VirtualFile> {
  val attachedObject = event.getAttachedObject()

  val psiFiles = (attachedObject as? TransferableWrapper)
    ?.getPsiElements()
    ?.filterIsInstance<PsiFileSystemItem>()
    ?.map { it.virtualFile }
    ?.takeIf { it.isNotEmpty() }

  return psiFiles ?: FileCopyPasteUtil.getVirtualFileListFromAttachedObject(attachedObject)
}
