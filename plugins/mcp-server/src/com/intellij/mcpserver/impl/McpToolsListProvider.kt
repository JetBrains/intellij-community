package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val logger = logger<McpToolsListProvider>()

/**
 * Provides a [StateFlow] of all available MCP tools.
 * Subscribes to [McpToolsProvider] and [McpToolset] extension point changes and updates the flow accordingly.
 *
 * The initial list is passed in rather than computed here: building it is reflection-heavy and must happen off the EDT,
 * so choosing *how* to compute it belongs to the caller. See [computeAllMcpTools] and [computeAllMcpToolsAsync].
 */
internal class McpToolsListProvider(private val scope: CoroutineScope, initialTools: ProviderTools) {
  private val _allTools = MutableStateFlow(initialTools.allTools)

  /**
   * StateFlow containing all currently available MCP tools from all providers.
   */
  val allTools: StateFlow<List<McpTool>> = _allTools.asStateFlow()

  /**
   * Tools of every provider converted so far. Lets an extension removal drop the affected tools immediately without
   * running the conversion, and lets a recomputation reuse the providers that did not change.
   */
  private val toolsByProvider = ConcurrentHashMap(initialTools.byProvider)

  /**
   * Serializes the recomputations, so that concurrent extension point changes cannot publish out of order.
   */
  private val recomputeMutex = Mutex()

  init {
    // Extension point callbacks are dispatched inside a write action on the EDT when a plugin is loaded or unloaded, so
    // they only invalidate the cached tools and request an update; the conversion itself runs in a coroutine
    // (IJPL-251556).
    McpToolsProvider.EP.addExtensionPointListener(scope, object : ExtensionPointListener<McpToolsProvider> {
      override fun extensionAdded(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        requestUpdate("McpToolsProvider extension added")
      }

      override fun extensionRemoved(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        dropTools(toolsByProvider.remove(extension))
        requestUpdate("McpToolsProvider extension removed")
      }
    })

    McpToolset.EP.addExtensionPointListener(scope, object : ExtensionPointListener<McpToolset> {
      override fun extensionAdded(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        invalidateReflectionProviders()
        requestUpdate("McpToolset extension added")
      }

      override fun extensionRemoved(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        invalidateReflectionProviders()
        // the category's fully qualified name is the toolset class name, see KClass<McpToolset>.asTools()
        val removedCategory = extension::class.qualifiedName
        dropTools(_allTools.value.filter { it.descriptor.category.fullyQualifiedName == removedCategory })
        requestUpdate("McpToolset extension removed")
      }
    })
  }

  /**
   * Each request gets its own job rather than feeding a long-lived consumer coroutine, so that a scope which waits for
   * its children (a `runBlocking` scope, for instance) is not held open by this provider.
   */
  private fun requestUpdate(reason: String) {
    scope.launch(Dispatchers.Default + CoroutineName("MCP tools list recomputation")) {
      recomputeMutex.withLock {
        logger.trace { "Emitting MCP all tools list update: reason=$reason" }
        val recomputed = computeAllMcpToolsAsync(toolsByProvider)
        toolsByProvider.keys.retainAll(recomputed.byProvider.keys)
        toolsByProvider.putAll(recomputed.byProvider)
        _allTools.value = recomputed.allTools
      }
    }
  }

  private fun dropTools(removed: List<McpTool>?) {
    if (removed.isNullOrEmpty()) return
    val removedSet = removed.toSet()
    _allTools.update { tools -> tools.filterNot { it in removedSet } }
  }

  private fun invalidateReflectionProviders() {
    for (provider in toolsByProvider.keys) {
      if (provider is ReflectionToolsProvider) toolsByProvider.remove(provider)
    }
  }

  /**
   * Tools of all providers, plus the per-provider breakdown needed to invalidate them selectively.
   */
  internal class ProviderTools(val byProvider: Map<McpToolsProvider, List<McpTool>>, val allTools: List<McpTool>)

  companion object {
    /**
     * Sequential fallback for the synchronous API. Prefer [computeAllMcpToolsAsync].
     */
    @RequiresBackgroundThread
    fun computeAllMcpTools(alreadyConverted: Map<McpToolsProvider, List<McpTool>> = emptyMap()): ProviderTools {
      return collectTools(alreadyConverted) { provider -> provider.getTools() }
    }

    suspend fun computeAllMcpToolsAsync(alreadyConverted: Map<McpToolsProvider, List<McpTool>> = emptyMap()): ProviderTools {
      return collectTools(alreadyConverted) { provider -> provider.getToolsAsync() }
    }

    private inline fun collectTools(
      alreadyConverted: Map<McpToolsProvider, List<McpTool>>,
      convert: (McpToolsProvider) -> List<McpTool>,
    ): ProviderTools {
      val byProvider = LinkedHashMap<McpToolsProvider, List<McpTool>>()
      for (provider in McpToolsProvider.EP.extensionList) {
        byProvider[provider] = alreadyConverted[provider] ?: try {
          convert(provider)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          logger.error("Cannot load tools for $provider", e)
          emptyList()
        }
      }
      return ProviderTools(byProvider, byProvider.values.flatten())
    }
  }
}
