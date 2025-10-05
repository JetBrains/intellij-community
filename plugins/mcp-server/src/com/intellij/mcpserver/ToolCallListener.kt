package com.intellij.mcpserver

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
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

class DirectoryCreatedEvent(val file: VirtualFile) : FileEvent
class DirectoryDeletedEvent(val file: VirtualFile) : FileEvent
class FileCreatedEvent(val file: VirtualFile, val content: String) : FileEvent
class FileDeletedEvent(val file: VirtualFile, val content: String?) : FileEvent
class FileMovedEvent(val file: VirtualFile, val oldParent: VirtualFile, val newParent: VirtualFile) : FileEvent
class FileContentChangeEvent(val file: VirtualFile, val oldContent: String?, val newContent: String) : FileEvent

fun CoroutineContext.reportToolActivity(@NlsContexts.Label toolDescription: String) {
  logger<ToolCallListener>().trace { "Tool '${currentToolDescriptor.name}' activity reported: $toolDescription" }
  application.messageBus.syncPublisher(ToolCallListener.TOPIC).toolActivity(currentToolDescriptor, toolDescription, mcpCallInfo)
}