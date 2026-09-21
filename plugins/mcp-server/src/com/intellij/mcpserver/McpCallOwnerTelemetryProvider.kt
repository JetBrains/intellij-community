package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<McpCallOwnerTelemetryProvider>()

/**
 * The agent chat and turn an MCP call belongs to, for analytics.
 *
 * The MCP server observes a call and never the conversation that caused it, so a tool call row could not be placed in
 * a chat or a turn. The owner of the session answers instead: it is the only side that knows which chat
 * `McpSessionOptions.ownerKey` names and which turn of it is open right now.
 */
@ApiStatus.Internal
data class McpCallOwnerIds(
  @JvmField val agentSessionId: String?,
  @JvmField val turnId: String?,
)

/**
 * Resolves [McpCallOwnerIds] for the sessions one agent surface owns.
 *
 * Implementations are asked on every tool call, from a `finally` block that may already be cancelled, so [resolve]
 * must not suspend, must not block, and must answer from state it already holds.
 */
@ApiStatus.Internal
interface McpCallOwnerTelemetryProvider {
  /** The ids of [ownerKey], or `null` when this provider does not own that key. Never a guess: an ambiguous key is `null`. */
  fun resolve(ownerKey: String): McpCallOwnerIds?

  companion object {
    val EP_NAME: ExtensionPointName<McpCallOwnerTelemetryProvider> = ExtensionPointName
      .create("com.intellij.mcpServer.mcpCallOwnerTelemetryProvider")
  }
}

/**
 * The ids of the chat behind [sessionOptions], or `null` when the IDE does not own the caller.
 *
 * A client the IDE did not launch has no owner key, which is the same population `launchOriginOf` reports as
 * `EXTERNAL_CLIENT`: no provider is asked, and the call reports no chat and no turn rather than an empty one.
 */
@ApiStatus.Internal
fun mcpCallOwnerIdsOf(sessionOptions: McpServerService.McpSessionOptions?): McpCallOwnerIds? {
  val ownerKey = sessionOptions?.ownerKey?.takeIf { it.isNotBlank() } ?: return null
  for (provider in McpCallOwnerTelemetryProvider.EP_NAME.extensionList) {
    val ids = try {
      provider.resolve(ownerKey)
    }
    catch (e: Throwable) {
      // One surface failing to answer must not fail the tool call it is only annotating.
      LOG.error("MCP call owner telemetry provider ${provider.javaClass.name} failed", e)
      null
    }
    if (ids != null) return ids
  }
  return null
}
