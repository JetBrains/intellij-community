package com.intellij.mcpserver

import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

interface ToolCallDocumentChangeListener {
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<ToolCallDocumentChangeListener> = Topic(ToolCallDocumentChangeListener::class.java)
  }

  fun documentsChanged(mcpToolDescriptor: McpToolDescriptor, events: List<DocumentChangeEvent>)
}

class DocumentChangeEvent(val document: Document, val file: VirtualFile, val oldContent: String?, val newContent: String)