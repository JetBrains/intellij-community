package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.hyperlinks.TerminalHyperlinkFilterContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel

internal class TerminalHyperlinkFilterContextImpl(
  sessionModel: TerminalSessionModel,
  override val eelDescriptor: EelDescriptor,
  coroutineScope: CoroutineScope,
) : TerminalHyperlinkFilterContext {

  @Volatile
  private var currentDirectoryInfo: DirectoryInfo = DirectoryInfo(null, null)

  private class DirectoryInfo(val path: String?, val virtualFile: VirtualFile?)

  init {
    coroutineScope.launch(Dispatchers.IO + CoroutineName(TerminalHyperlinkFilterContextImpl::class.java.simpleName)) {
      sessionModel.terminalState.collect {
        val currentDirectory = it.currentDirectory
        if (currentDirectory != currentDirectoryInfo.path) {
          currentDirectoryInfo = DirectoryInfo(currentDirectory, findVirtualDirectory(currentDirectory))
        }
      }
    }
  }

  override val currentWorkingDirectory: VirtualFile?
    get() = currentDirectoryInfo.virtualFile?.takeIf { it.isValid }

  private fun findVirtualDirectory(directory: @NativePath String?): VirtualFile? {
    if (directory.isNullOrBlank()) return null
    val eelPath = try {
      EelPath.parse(directory, eelDescriptor)
    }
    catch (e: EelPathException) {
      logger<TerminalHyperlinkFilterContextImpl>().info("Failed to parse path: $directory, $eelDescriptor", e)
      return null
    }
    val nioPath = try {
      eelPath.asNioPath()
    }
    catch (e: IllegalArgumentException) {
      logger<TerminalHyperlinkFilterContextImpl>().info("FileSystem not found for path: $directory, $eelDescriptor", e)
      return null
    }
    return VirtualFileManager.getInstance().findFileByNioPath(nioPath)?.takeIf {
      it.isValid && it.isDirectory
    }
  }
}
