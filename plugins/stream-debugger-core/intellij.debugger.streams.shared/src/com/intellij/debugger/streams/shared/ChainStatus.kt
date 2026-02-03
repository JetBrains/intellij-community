package com.intellij.debugger.streams.shared

import kotlinx.serialization.Serializable

@Serializable
enum class ChainStatus {
  LANGUAGE_NOT_SUPPORTED,
  COMPUTING,
  FOUND,
  NOT_FOUND
}
