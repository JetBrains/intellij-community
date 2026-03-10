// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.util.NlsSafe


class ToolchainRequirement private constructor(
  val type: String,
  paramsMap: Map<String, String>,
) {
  val params: Map<String, String> = HashMap(paramsMap)
  val description: @NlsSafe String = "$type ${paramsMap.entries.joinToString { "${it.key}=${it.value}" }}"


  class Builder(val type: String) {
    private val map = HashMap<String, String>()
    fun set(name: String, value: String): Builder {
      map[name] = value
      return this
    }

    fun build(): ToolchainRequirement = ToolchainRequirement(type, map)
  }

  companion object {
    const val JDK_TYPE: String = "jdk"

  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ToolchainRequirement) return false

    if (type != other.type) return false
    if (params != other.params) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + params.hashCode()
    return result
  }

  override fun toString(): String {
    return description
  }
}
