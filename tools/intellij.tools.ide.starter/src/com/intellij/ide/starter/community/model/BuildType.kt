package com.intellij.ide.starter.community.model

enum class BuildType(val type: String) {
  RELEASE("release"),
  EAP("eap"),
  PREVIEW("preview"),
  NIGHTLY("nightly"),
  RC("rc");

  companion object {
    fun fromString(type: String): BuildType = entries.firstOrNull { it.type == type.lowercase() }
                                              ?: error("Unknown release type $type. Possible values: ${entries.joinToString(",")})")
  }
}