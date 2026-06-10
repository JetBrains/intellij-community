package com.intellij.terminal.frontend.action

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.frontend.toolwindow.impl.createTerminalTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Copy of [org.jetbrains.plugins.terminal.action.RevealFileInTerminalAction], but frontend-only.
 * Also, the requirement for virtual file to be in the local file system is relaxed.
 *
 * This action is enabled only when [TerminalEngine.REWORKED] is enabled.
 * It is required because the Reworked Terminal should be opened from the frontend.
 *
 * If terminal engine is not [TerminalEngine.REWORKED], then this action will be disabled and original
 * [org.jetbrains.plugins.terminal.action.RevealFileInTerminalAction] will be performed on the backend instead.
 * Classic Terminal requires opening the session with the specified working directory only on backend.
 */
internal class RevealFileInReworkedTerminalAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = getContextFile(e) ?: return
    e.coroutineScope.launch(Dispatchers.IO) {
      val path = getNearestDirectoryPath(file, project) ?: return@launch
      withContext(Dispatchers.EDT) {
        createTerminalTab(project, workingDirectory = path.toString())
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isAvailable(e)
  }

  private fun isAvailable(e: AnActionEvent): Boolean {
    if (TerminalOptionsProvider.instance.terminalEngine != TerminalEngine.REWORKED) {
      return false
    }

    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    return project != null && !LightEdit.owns(project) && getContextFile(e) != null &&
           (!e.isFromContextMenu || editor == null || !editor.getSelectionModel().hasSelection())
  }

  private fun getContextFile(e: AnActionEvent): VirtualFile? {
    return e.getData(CommonDataKeys.VIRTUAL_FILE)
  }

  private fun getNearestDirectoryPath(file: VirtualFile, project: Project): Path? {
    val nioPath = file.toNioPathOrNull()
    if (nioPath != null) {
      return getNearestDirectory(nioPath)
    }

    // In the case of Remote Dev, `file.toNioPath` is not implemented.
    // And `file.path` is a local path in the remote environment.
    // So, let's try to get the remote NIO path to start a Terminal process in the correct environment.
    val remotePath = getRemotePath(file.path, project)
    return if (remotePath != null) getNearestDirectory(remotePath) else null
  }

  private fun getRemotePath(path: @NativePath String, project: Project): Path? {
    val eelDescriptor = project.getEelDescriptor()
    val eelPath = try {
      EelPath.parse(path, eelDescriptor)
    }
    catch (e: EelPathException) {
      thisLogger().warn("Failed to parse EelPath for path: $path", e)
      return null
    }

    return try {
      eelPath.asNioPath()
    }
    catch (e: IllegalArgumentException) {
      thisLogger().warn("Failed to convert EelPath to Nio Path for path: $path", e)
      return null
    }
  }

  private fun getNearestDirectory(path: Path): Path {
    return if (path.isDirectory()) path else path.parent
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}