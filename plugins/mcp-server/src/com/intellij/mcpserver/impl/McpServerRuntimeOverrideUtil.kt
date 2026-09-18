package com.intellij.mcpserver.impl

import com.intellij.mcpserver.impl.util.network.isValidPort
import com.intellij.util.SystemProperties

internal const val IJ_MCP_FORCE_ENABLE_PROPERTY: String = "idea.mcp.server.force.enable"
internal const val IJ_MCP_FORCE_PORT_PROPERTY: String = "idea.mcp.server.force.port"

internal sealed interface ForcedPortState {
  data object Absent : ForcedPortState

  data class Valid(val port: Int) : ForcedPortState

  data class Invalid(val rawValue: String) : ForcedPortState
}

internal fun isMcpServerForceEnabled(): Boolean =
  SystemProperties.getBooleanProperty(IJ_MCP_FORCE_ENABLE_PROPERTY, false)

internal fun hasMcpServerRuntimeOverrides(): Boolean =
  isMcpServerForceEnabled() || System.getProperty(IJ_MCP_FORCE_PORT_PROPERTY) != null

internal fun isMcpServerEffectivelyEnabled(savedEnabled: Boolean): Boolean =
  savedEnabled || isMcpServerForceEnabled()

internal fun getForcedMcpServerPortOrNull(): Int? = when (val state = getForcedMcpServerPortState()) {
  is ForcedPortState.Valid -> state.port
  else -> null
}

internal fun getForcedMcpServerPortState(): ForcedPortState {
  val rawValue = System.getProperty(IJ_MCP_FORCE_PORT_PROPERTY) ?: return ForcedPortState.Absent
  val port = rawValue.toIntOrNull() ?: return ForcedPortState.Invalid(rawValue)
  if (!isValidPort(port)) return ForcedPortState.Invalid(rawValue)
  return ForcedPortState.Valid(port)
}
