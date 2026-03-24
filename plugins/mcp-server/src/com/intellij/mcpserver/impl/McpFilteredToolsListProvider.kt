package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.util.coroutines.childScope
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

private val logger = logger<McpFilteredToolsListProvider>()

/**
 * Provides MCP tools for a session, handling filtering and updates.
 */
internal class McpFilteredToolsListProvider(
  parentScope: CoroutineScope,
  private val sessionOptions: McpServerService.McpSessionOptions,
  private val mcpServerService: McpServerService,
  private val useFiltersFromEP: Boolean,
  private val invocationMode: McpToolInvocationMode = McpToolInvocationMode.DIRECT,
) {
  private val scope = parentScope.childScope("McpToolsProvider")
  private val clientInfo = MutableStateFlow<Implementation?>(null)
  val mcpTools = MutableStateFlow(mcpServerService.getMcpTools(
    filter = sessionOptions.toolFilter,
    clientInfo = null,
    sessionOptions = sessionOptions,
    useFiltersFromEP = useFiltersFromEP,
    invocationMode = invocationMode))

  private val filterProvidersScope = AtomicReference<CoroutineScope?>(null)

  init {
    // Subscribe to changes from McpToolsStateProvider
    scope.launch {
      mcpServerService.toolsStateProvider.allTools.collectLatest {
        emitMcpTools("Tools from McpToolsStateProvider updated")
      }
    }

    // Subscribe to McpToolFilterProvider extension point changes
    McpToolFilterProvider.EP.addExtensionPointListener(scope, object : ExtensionPointListener<McpToolFilterProvider> {
      override fun extensionAdded(extension: McpToolFilterProvider, pluginDescriptor: PluginDescriptor) {
        subscribeToFilterProviders(clientInfo.value, sessionOptions)
        emitMcpTools("McpToolFilterProvider extension added")
      }

      override fun extensionRemoved(extension: McpToolFilterProvider, pluginDescriptor: PluginDescriptor) {
        subscribeToFilterProviders(clientInfo.value, sessionOptions)
        emitMcpTools("McpToolFilterProvider extension removed")
      }
    })

    // Initial subscription to filter providers
    subscribeToFilterProviders(clientInfo.value, sessionOptions)
  }

  fun updateClientInfo(newClientInfo: Implementation) {
    clientInfo.value = newClientInfo
    // Re-subscribe to filter providers with the new clientInfo
    subscribeToFilterProviders(newClientInfo, sessionOptions)
    // Re-fetch MCP tools with the new clientInfo
    emitMcpTools("Session initialized with clientVersion=${newClientInfo.name}")
  }

  private fun emitMcpTools(reason: String) {
    val currentClientInfo = clientInfo.value
    logger.trace {
      "Emitting MCP tools update: reason=$reason, clientName=${currentClientInfo?.name}, " +
      "localAgentId=${sessionOptions.localAgentId}"
    }
    mcpTools.tryEmit(mcpServerService.getMcpTools(
      filter = sessionOptions.toolFilter,
      clientInfo = currentClientInfo,
      sessionOptions = sessionOptions,
      useFiltersFromEP = useFiltersFromEP,
      invocationMode = invocationMode))
  }

  private fun subscribeToFilterProviders(clientInfoValue: Implementation?, sessionOptionsValue: McpServerService.McpSessionOptions?) {
    filterProvidersScope.getAndSet(scope.childScope("subscribeToFilterProviders"))?.cancel()
    val currentScope = filterProvidersScope.get() ?: return
    McpToolFilterProvider.EP.extensionList.forEach { provider ->
      currentScope.launch {
        provider.getUpdates(clientInfoValue, currentScope, sessionOptionsValue, invocationMode).collectLatest {
          emitMcpTools("Filter provider update from ${provider.javaClass.simpleName}")
        }
      }
    }
  }
}
