package com.intellij.terminal.frontend.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileCopyPasteUtil.getFileListFromAttachedObject
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.plugins.terminal.fus.TerminalInsertedContentType
import java.awt.datatransfer.DataFlavor
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal class TerminalDropData(event: DnDEvent) {
  val virtualFiles: List<VirtualFile> = (event.attachedObject as? TransferableWrapper)
                                          ?.getVirtualFiles()
                                          ?.toList()
                                        ?: emptyList()

  val paths: List<Path> = if (virtualFiles.isEmpty()) {
    getFileListFromAttachedObject(event.attachedObject).map { it.toPath() }
  }
  else emptyList()

  /** Plain text payload of a drop that carries no files; inserted into the terminal as is. */
  val text: String? = if (virtualFiles.isEmpty() && paths.isEmpty()) {
    getDroppedText(event.attachedObject)
  }
  else null

  @RequiresBackgroundThread
  fun getContentType(): TerminalInsertedContentType {
    return when {
      virtualFiles.size > 1 -> TerminalInsertedContentType.MULTIPLE_ITEMS

      virtualFiles.size == 1 ->
        if (virtualFiles.single().isDirectory) {
          TerminalInsertedContentType.DIRECTORY
        }
        else {
          TerminalInsertedContentType.FILE
        }

      paths.size > 1 -> TerminalInsertedContentType.MULTIPLE_ITEMS

      paths.size == 1 ->
        if (paths.single().isDirectory()) {
          TerminalInsertedContentType.DIRECTORY
        }
        else {
          TerminalInsertedContentType.FILE
        }

      text != null -> TerminalInsertedContentType.TEXT

      else -> error("TerminalDropData contains no dropped content")
    }
  }
}

private fun getDroppedText(attachedObject: Any?): String? {
  val transferable = (attachedObject as? DnDNativeTarget.EventInfo)?.transferable ?: return null
  return try {
    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      transferable.getTransferData(DataFlavor.stringFlavor) as? String
    }
    else null
  }
  catch (_: Exception) {
    null
  }
}
