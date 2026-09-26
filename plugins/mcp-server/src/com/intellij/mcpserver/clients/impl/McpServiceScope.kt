package com.intellij.mcpserver.clients.impl

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
internal class McpServiceScope(val scope: CoroutineScope)