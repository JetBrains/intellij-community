package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.completion.escapeShellArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.startup.TerminalLocalPathTranslator
import org.jetbrains.plugins.terminal.util.getNow
import java.nio.file.Path

internal object TerminalFilePathHandler {
  fun getFilesAsText(files: List<VirtualFile>, context: TerminalProcessContext): String {
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

  private fun translateLocalPathToWsl(nioPath: Path, eelDescriptor: EelDescriptor): String? {
    val fileDescriptor = nioPath.getEelDescriptor()
    if (fileDescriptor != LocalEelDescriptor || !LocalEelDescriptor.osFamily.isWindows || !eelDescriptor.osFamily.isPosix) {
      return null
    }

    return TerminalLocalPathTranslator(eelDescriptor).translateAbsoluteLocalPathToRemote(nioPath)?.toString()
  }

  internal fun isSameEnvironment(filePath: Path, eelDescriptor: EelDescriptor): Boolean {
    val fileMachine = filePath.getEelDescriptor().getResolvedEelMachine() ?: return false
    val eelMachine = eelDescriptor.getResolvedEelMachine() ?: return false
    return fileMachine == eelMachine
  }

  internal suspend fun resolveVirtualFiles(paths: List<Path>): List<VirtualFile> = withContext(Dispatchers.IO) {
    paths.mapNotNull { path -> VfsUtil.findFile(path, true) }
  }
}

internal data class TerminalProcessContext(
  val eelDescriptor: EelDescriptor,
  val shellName: ShellName,
)

internal fun getTerminalContext(terminalView: TerminalView): TerminalProcessContext? {
  val eelDescriptor = terminalView.sessionDeferred.getNow()?.eelDescriptor ?: return null
  val shellName = terminalView.startupOptionsDeferred.getNow()?.guessShellName() ?: ShellName.of("unknown")
  return TerminalProcessContext(eelDescriptor, shellName)
}