package com.intellij.mermaid.build

enum class PublishChannel(val actualName: String) {
  NIGHTLY("nightly"),
  STABLE("");

  companion object {
    fun parse(value: String?): PublishChannel {
      val name = value.orEmpty().toLowerCase()
      val channel = values().firstOrNull { it.actualName == name }
      checkNotNull(channel) { "Failed to parse publish channel name" }
      return channel
    }
  }
}
