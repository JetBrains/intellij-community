package com.intellij.mcpserver.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.mcpserver.DirectoryCreatedEvent
import com.intellij.mcpserver.DirectoryDeletedEvent
import com.intellij.mcpserver.FileContentChangeEvent
import com.intellij.mcpserver.FileCreatedEvent
import com.intellij.mcpserver.FileDeletedEvent
import com.intellij.mcpserver.FileMovedEvent
import com.intellij.mcpserver.McpToolSideEffectEvent
import com.intellij.mcpserver.mcpCallInfoOrNull
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

private val logger = Logger.getInstance("McpSideEffectProcessing")

internal class SideEffectResult<T>(
  val result: T,
  val events: List<McpToolSideEffectEvent>,
  val vfsEventCount: Int,
  val documentChangeCount: Int,
)

internal suspend fun <T> processSideEffects(
  callId: Int,
  block: suspend () -> T,
): SideEffectResult<T> = coroutineScope {
  val vfsEvents = CopyOnWriteArrayList<VFileEvent>()
  val initialDocumentContents = ConcurrentHashMap<Document, String>()

  VirtualFileManager.getInstance().addAsyncFileListener(this, AsyncFileListener { events ->
    val inHandlerInfo = currentThreadContext().mcpCallInfoOrNull
    if (inHandlerInfo != null && inHandlerInfo.callId == callId) {
      logger.trace { "VFS changes detected for call: $inHandlerInfo" }
      vfsEvents.addAll(events)
    }
    // probably we have to read initial contents here
    // see comment below near `is VFileContentChangeEvent`
    return@AsyncFileListener object : AsyncFileListener.ChangeApplier {}
  })

  val documentListener = object : DocumentListener {
    // record content before any change
    override fun beforeDocumentChange(event: DocumentEvent) {
      val inHandlerInfo = currentThreadContext().mcpCallInfoOrNull
      if (inHandlerInfo != null && inHandlerInfo.callId == callId) {
        logger.trace { "Document changes detected for call: $inHandlerInfo" }
        initialDocumentContents.computeIfAbsent(event.document) { event.document.text }
      }
    }
  }

  EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this.asDisposable())

  val result = block()

  val sideEffectEvents = try {
    collectSideEffectEvents(initialDocumentContents, vfsEvents)
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (t: Throwable) {
    logger.error("Failed to process changed documents after MCP tool call", t)
    emptyList()
  }

  if (sideEffectEvents.isNotEmpty()) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
  }

  SideEffectResult(
    result = result,
    events = sideEffectEvents,
    vfsEventCount = vfsEvents.size,
    documentChangeCount = initialDocumentContents.size,
  )
}

private suspend fun collectSideEffectEvents(
  initialDocumentContents: Map<Document, String>,
  vfsEvents: List<VFileEvent>,
): List<McpToolSideEffectEvent> {
  val events = mutableListOf<McpToolSideEffectEvent>()
  val processedChangedFiles = mutableSetOf<VirtualFile>()

  for ((doc, oldContent) in initialDocumentContents) {
    val virtualFile = FileDocumentManager.getInstance().getFile(doc) ?: continue
    val newContent = readAction { doc.text }
    events.add(FileContentChangeEvent(virtualFile, oldContent, newContent))
    processedChangedFiles.add(virtualFile)
  }

  for (event in vfsEvents) {
    when (event) {
      is VFileMoveEvent -> {
        events.add(FileMovedEvent(event.file, event.oldParent, event.newParent))
      }
      is VFileCreateEvent -> {
        val virtualFile = event.file ?: continue
        if (event.isDirectory) {
          events.add(DirectoryCreatedEvent(virtualFile))
        }
        else {
          val newContent =
            readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text }
            ?: continue
          events.add(FileCreatedEvent(virtualFile, newContent))
        }
      }
      is VFileDeleteEvent -> {
        val virtualFile = event.file
        if (virtualFile.isDirectory) {
          events.add(DirectoryDeletedEvent(virtualFile))
        }
        else {
          val document =
            readAction { FileDocumentManager.getInstance().getDocument(virtualFile) } ?: continue
          val oldContent = initialDocumentContents[document]
          events.add(FileDeletedEvent(virtualFile, oldContent))
        }
      }
      is VFileCopyEvent -> {
        val createdFile = event.findCreatedFile() ?: continue
        val newContent =
          readAction { FileDocumentManager.getInstance().getDocument(createdFile)?.text } ?: continue
        events.add(FileCreatedEvent(createdFile, newContent))
      }
      is VFileContentChangeEvent -> {
        // reported in documents loop
        if (processedChangedFiles.contains(event.file)) continue
        val virtualFile = event.file
        val newContent =
          readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
        // Important: there may be a case when file is changed via low-level change (like File.replaceText).
        // in this case we don't track the old content, because it may be heavy, it requires loading the file in
        // AsyncFileListener above and decoding with encoding etc. The file can be binary etc.
        events.add(FileContentChangeEvent(virtualFile, oldContent = null, newContent = newContent))
      }
    }
  }

  return events
}