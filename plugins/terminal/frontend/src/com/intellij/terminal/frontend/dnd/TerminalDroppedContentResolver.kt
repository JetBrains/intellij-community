package com.intellij.terminal.frontend.dnd

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.terminal.frontend.toolwindow.impl.TerminalFilePathHandler.getPathAsText
import com.intellij.terminal.frontend.toolwindow.impl.TerminalProcessContext
import java.nio.file.Path

internal object TerminalDroppedContentResolver {
  fun resolveText(
    data: TerminalDropData,
    terminalContext: TerminalProcessContext,
    projectEelDescriptor: EelDescriptor,
  ): String? {
    return when {
      data.virtualFiles.isNotEmpty() ->
        getVirtualFilesAsText(data.virtualFiles, terminalContext, projectEelDescriptor)
      data.paths.isNotEmpty() ->
        getPathAsText(data.paths, terminalContext)
      else ->
        data.text
    }
  }

  fun resolveFilePaths(
    data: TerminalDropData,
    projectEelDescriptor: EelDescriptor,
  ): List<Path> {
    return when {
      data.virtualFiles.isNotEmpty() ->
        data.virtualFiles.mapNotNull { getNioPathForFile(it, projectEelDescriptor) }
      else ->
        data.paths
    }
  }

  private fun getNioPathForFile(file: VirtualFile, projectEelDescriptor: EelDescriptor): Path? {
    file.toNioPathOrNull()?.let { return it }

    // Handle the case of ThinClientNodeVirtualFile (file dropped from the Project View in RemDev).
    // It doesn't implement [VirtualFile.toNioPathOrNull], so we need to reconstruct the path manually.
    return if (IdeProductMode.isFrontend) {
      try {
        EelPath.parse(file.path, projectEelDescriptor).asNioPath()
      }
      catch (_: EelPathException) {
        null
      }
      catch (_: IllegalArgumentException) {
        null
      }
    }
    else null
  }

  private fun getVirtualFilesAsText(
    files: List<VirtualFile>,
    terminalContext: TerminalProcessContext,
    projectEelDescriptor: EelDescriptor,
  ): String {
    val paths = files.mapNotNull {
      getNioPathForFile(it, projectEelDescriptor)
    }
    return getPathAsText(paths, terminalContext)
  }
}