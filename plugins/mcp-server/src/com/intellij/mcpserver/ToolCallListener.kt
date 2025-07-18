package com.intellij.mcpserver

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.application
import com.intellij.util.messages.Topic
import kotlin.coroutines.CoroutineContext

interface ToolCallListener {
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<ToolCallListener> = Topic(ToolCallListener::class.java)
  }

  fun beforeMcpToolCall(mcpToolDescriptor: McpToolDescriptor, additionalData: McpCallInfo) {}

  fun afterMcpToolCall(mcpToolDescriptor: McpToolDescriptor, events: List<McpToolSideEffectEvent>, error: Throwable?, callInfo: McpCallInfo) {}

  fun toolActivity(mcpToolDescriptor: McpToolDescriptor, @NlsContexts.Label toolActivityDescription: String, callInfo: McpCallInfo) {}
}

sealed interface McpToolSideEffectEvent

sealed interface FileEvent: McpToolSideEffectEvent

class FileCreatedEvent(val file: VirtualFile, val content: String) : FileEvent
class FileDeletedEvent(val file: VirtualFile, val content: String?) : FileEvent
class FileMovedEvent(val file: VirtualFile, val oldParent: VirtualFile, val newParent: VirtualFile) : FileEvent
class FileContentChangeEvent(val file: VirtualFile, val oldContent: String?, val newContent: String) : FileEvent

fun CoroutineContext.reportToolActivity(@NlsContexts.Label toolDescription: String) {
  application.messageBus.syncPublisher(ToolCallListener.TOPIC).toolActivity(this.currentToolDescriptor, toolDescription, this.mcpCallInfo)
}