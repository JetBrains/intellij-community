package com.intellij.debugger.streams.shared

import kotlinx.serialization.Serializable

/**
 * Projection of the stream-chain status sent over RPC to the frontend toolbar action.
 * @see com.intellij.debugger.streams.core.action.ChainStatus
 */
@Serializable
enum class ChainStatusDto {
  LANGUAGE_NOT_SUPPORTED,
  COMPUTING,
  FOUND,
  NOT_FOUND
}
