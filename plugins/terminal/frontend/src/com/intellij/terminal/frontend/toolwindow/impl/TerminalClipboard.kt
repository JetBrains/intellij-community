package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

internal object TerminalClipboard {
  fun pasteClipboardContent(
    view: TerminalView,
    scrollingModel: TerminalOutputScrollingModel? = null,
    preferSystemSelection: Boolean = false,
  ) {
    val content = CopyPasteManager.getInstance().contents ?: return
    view.coroutineScope.launch {
      val text = getContentAsText(view, content) ?: return@launch

      withContext(Dispatchers.EDT) {
        view.createSendTextBuilder()
          .useBracketedPasteMode()
          .send(text)

        // Scroll to the cursor if the scrolling model is available in this editor.
        // It can be absent if it is the alternate buffer editor.
        scrollingModel?.scrollToCursor(force = true)
      }
    }
  }

  private suspend fun getContentAsText(view: TerminalView, content: Transferable): String? {
    val terminalContext = getTerminalContext(view) ?: return null

    return try {
      when {
        content.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> getFilePathsAsText(content, terminalContext)
        content.isDataFlavorSupported(DataFlavor.imageFlavor) -> extractImageAsTempFilePath(content, terminalContext.eelDescriptor)
        content.isDataFlavorSupported(DataFlavor.stringFlavor) -> getStringAsText(content)
        else -> null
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      logger<TerminalClipboard>().error("Failed to get text from clipboard", t)
      null
    }
  }

  private suspend fun getFilePathsAsText(content: Transferable, terminalContext: TerminalContext): String? = withContext(Dispatchers.IO) {
    val files = content.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return@withContext null

    val paths = files.filterIsInstance<File>().map { it.toPath() }
    val copiedFiles = resolveVirtualFiles(paths)

    FilePathsHandler.getFilesAsText(copiedFiles, terminalContext)
  }

  private suspend fun extractImageAsTempFilePath(
    content: Transferable,
    eelDescriptor: EelDescriptor,
  ): String? = withContext(Dispatchers.IO) {
    val image = content.getTransferData(DataFlavor.imageFlavor) as? Image ?: return@withContext null

    val eelApi = eelDescriptor.toEelApi()
    val tempFile = eelApi.fs.createTemporaryFile()
      .prefix("terminal-paste-image-")
      .suffix(".png")
      .deleteOnExit(true)
      .getOrThrow()

    val nioPath = tempFile.asNioPath()
    val written = Files.newOutputStream(nioPath).use { output ->
      ImageIO.write(ImageUtil.toBufferedImage(image), "png", output)
    }
    if (!written) {
      return@withContext null
    }

    tempFile.toString()
  }

  private suspend fun getStringAsText(content: Transferable): String? = withContext(Dispatchers.IO) {
    content.getTransferData(DataFlavor.stringFlavor) as? String
  }
}