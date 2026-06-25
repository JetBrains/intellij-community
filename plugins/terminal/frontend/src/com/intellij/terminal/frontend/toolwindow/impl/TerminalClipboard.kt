package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics
import org.jetbrains.plugins.terminal.fus.TerminalInsertedContentSource
import org.jetbrains.plugins.terminal.fus.TerminalInsertedContentType
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.isDirectory

internal object TerminalClipboard {
  fun pasteClipboardContent(
    project: Project,
    view: TerminalView,
    preferSystemSelection: Boolean = false,
  ) {
    val systemContents = CopyPasteManager.getInstance().systemSelectionContents
    val defaultContents = CopyPasteManager.getInstance().contents

    view.coroutineScope.launch {
      val result: ContentToTextConversionResult = sequenceOf(if (preferSystemSelection) systemContents else null, defaultContents)
                   .firstNotNullOfOrNull { getContentAsText(it, view) }
                   ?.takeIf { it.text.isNotEmpty() }
                   ?: return@launch

      view.createSendTextBuilder()
        .useBracketedPasteMode()
        .send(result.text)

      val commandLine = view.getRunningProcessCommandLine()
      val processExecutable = commandLine?.let {
        TerminalCommandUsageStatistics.getLoggableCommandData(commandLine, expandAbsoluteOrRelativePath = true).command
      }
      ReworkedTerminalUsageCollector.logContentInserted(
        project = project,
        contentType = result.contentType,
        fileSource = TerminalInsertedContentSource.CLIPBOARD,
        processExecutable = processExecutable,
      )
    }
  }

  private suspend fun getContentAsText(content: Transferable?, view: TerminalView): ContentToTextConversionResult? {
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
  ): ContentToTextConversionResult? = withContext(Dispatchers.IO) {
    val files = content.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return@withContext null
    @Suppress("IO_FILE_USAGE")
    val paths = files.filterIsInstance<File>().map { it.toPath() }
    if (paths.isEmpty()) {
      return@withContext null
    }

    val text = TerminalFilePathHandler.getPathAsText(paths, terminalContext)
    val contentType = when {
      paths.size > 1 -> TerminalInsertedContentType.MULTIPLE_ITEMS
      paths.single().isDirectory() -> TerminalInsertedContentType.DIRECTORY
      else -> TerminalInsertedContentType.FILE
    }
    ContentToTextConversionResult(text, contentType)
  }

  private suspend fun extractImageAsTempFilePath(
    content: Transferable,
    eelDescriptor: EelDescriptor,
  ): ContentToTextConversionResult? = withContext(Dispatchers.IO) {
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

    ContentToTextConversionResult(tempFile.toString(), TerminalInsertedContentType.CLIPBOARD_IMAGE)
  }

  private suspend fun getStringAsText(content: Transferable): ContentToTextConversionResult? = withContext(Dispatchers.IO) {
    val text = content.getTransferData(DataFlavor.stringFlavor) as? String ?: return@withContext null
    ContentToTextConversionResult(text, TerminalInsertedContentType.TEXT)
  }

  private data class ContentToTextConversionResult(
    val text: String,
    val contentType: TerminalInsertedContentType,
  )
}