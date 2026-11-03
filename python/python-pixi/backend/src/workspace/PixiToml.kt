// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.core.io.ParsingException
import com.electronwill.nightconfig.toml.TomlParser

internal fun parsePixiToml(text: String): PixiTomlTable? = try {
  PixiTomlTable(TomlParser().parse(text))
}
catch (_: ParsingException) {
  null
}

internal class PixiTomlTable(private val config: UnmodifiableConfig) {
  fun get(path: String): Any? = get(path.split('.'))

  fun get(path: List<String>): Any? = wrap(config.getRaw(path))

  fun getTable(path: String): PixiTomlTable? = get(path) as? PixiTomlTable

  fun getTable(path: List<String>): PixiTomlTable? = get(path) as? PixiTomlTable

  fun getArray(path: String): List<*>? = get(path) as? List<*>

  fun getArray(path: List<String>): List<*>? = get(path) as? List<*>

  fun getString(path: String): String? = get(path) as? String

  fun getBoolean(path: String): Boolean? = get(path) as? Boolean

  fun keySet(): Set<String> = config.entrySet().mapTo(linkedSetOf()) { it.key }

  private fun wrap(value: Any?): Any? = when (value) {
    is UnmodifiableConfig -> PixiTomlTable(value)
    is List<*> -> value.map(::wrap)
    else -> value
  }
}
