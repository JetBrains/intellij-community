// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.util.NlsSafe


class ToolchainRequirement private constructor(
  val type: String,
  paramsMap: Map<String, String>,
  val useImporterJdkIfMatches: Boolean,
  val discoverJdks: Boolean,
) {
  val params: Map<String, String> = HashMap(paramsMap)
  val description: @NlsSafe String = "$type ${paramsMap.entries.joinToString { "${it.key}=${it.value}" }}"


  class Builder(val type: String) {
    private val map = HashMap<String, String>()
    private var useImporterJdkIfMatches = false
    private var discoverJdks = false

    fun set(name: String, value: String): Builder {
      map[name] = value
      return this
    }

    fun useImporterJdkIfMatches(value: Boolean): Builder {
      useImporterJdkIfMatches = value
      return this
    }

    fun discoverJdks(value: Boolean): Builder {
      discoverJdks = value
      return this
    }

    fun build(): ToolchainRequirement = ToolchainRequirement(type, map, useImporterJdkIfMatches, discoverJdks)
  }

  companion object {
    const val JDK_TYPE: String = "jdk"

  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ToolchainRequirement) return false

    if (type != other.type) return false
    if (params != other.params) return false
    if (useImporterJdkIfMatches != other.useImporterJdkIfMatches) return false
    if (discoverJdks != other.discoverJdks) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + params.hashCode()
    result = 31 * result + useImporterJdkIfMatches.hashCode()
    result = 31 * result + discoverJdks.hashCode()
    return result
  }

  override fun toString(): String {
    return description
  }
}
