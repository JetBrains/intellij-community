package com.intellij.mcpserver

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

interface ToolCallListener {
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<ToolCallListener> = Topic(ToolCallListener::class.java)
  }

  fun beforeMcpToolCall(mcpToolDescriptor: McpToolDescriptor) {}

  fun afterMcpToolCall(mcpToolDescriptor: McpToolDescriptor, events: List<McpToolSideEffectEvent>) {}
}

sealed interface McpToolSideEffectEvent

sealed interface FileEvent: McpToolSideEffectEvent

class FileCreatedEvent(val file: VirtualFile, val content: String) : FileEvent
class FileDeletedEvent(val file: VirtualFile, val content: String?) : FileEvent
class FileMovedEvent(val file: VirtualFile, val oldParent: VirtualFile, val newParent: VirtualFile) : FileEvent
class FileContentChangeEvent(val file: VirtualFile, val oldContent: String?, val newContent: String) : FileEvent
