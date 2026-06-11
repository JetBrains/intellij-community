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
    val systemContents = CopyPasteManager.getInstance().systemSelectionContents
    val defaultContents = CopyPasteManager.getInstance().contents

    view.coroutineScope.launch {
      val text = sequenceOf(if (preferSystemSelection) systemContents else null, defaultContents)
                   .firstNotNullOfOrNull { getContentAsText(it, view) } ?: return@launch

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

  private suspend fun getContentAsText(content: Transferable?, view: TerminalView): String? {
    if (content == null) return null
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

  private suspend fun getFilePathsAsText(
    content: Transferable,
    terminalContext: TerminalProcessContext,
  ): String? = withContext(Dispatchers.IO) {
    val files = content.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return@withContext null
    val paths = files.filterIsInstance<File>().map { it.toPath() }

    TerminalFilePathHandler.getPathAsText(paths, terminalContext)
  }

  private suspend fun extractImageAsTempFilePath(
    content: Transferable,
    eelDescriptor: EelDescriptor,
  ): String? = withContext(Dispatchers.IO) {
    val image = content.getTransferData(DataFlavor.imageFlavor) as? Image ?: return@withContext null

    val eelApi = eelDescriptor.toEelApi()
    val tempFile = eelApi.fs.createTemporaryFile()
      .prefix("pasted-image-")
      .suffix(".png")
      .deleteOnExit(true)
      .getOrThrow()

    val nioPath = tempFile.asNioPath()
    val written = Files.newOutputStream(nioPath).use { output ->
      ImageIO.write(ImageUtil.toBufferedImage(image), "png", output)
    }
    if (!written) {
      error("Failed to write clipboard image to temporary file")
    }

    tempFile.toString()
  }

  private suspend fun getStringAsText(content: Transferable): String? = withContext(Dispatchers.IO) {
    content.getTransferData(DataFlavor.stringFlavor) as? String
  }
}