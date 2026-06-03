package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalHyperlinkFilterContext

internal class TerminalHyperlinkFilterContextImpl(override val eelDescriptor: EelDescriptor) : TerminalHyperlinkFilterContext {
  @Volatile
  private var workingDirectory: VirtualFile? = null

  override val currentWorkingDirectory: VirtualFile?
    get() = workingDirectory?.takeIf { it.isValid }

  fun updateCurrentDirectory(directory: @NativePath String?) {
    workingDirectory = findVirtualDirectory(directory)
  }

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
